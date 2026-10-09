package com.quant.strategy.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quant.common.exception.BizException;
import com.quant.common.result.PageResult;
import com.quant.common.util.JsonUtils;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.strategy.dto.BacktestBatchDetailVO;
import com.quant.strategy.dto.BatchBacktestRequest;
import com.quant.strategy.entity.BacktestBatch;
import com.quant.strategy.entity.BacktestRecord;
import com.quant.strategy.mapper.BacktestBatchMapper;
import com.quant.strategy.mapper.BacktestRecordMapper;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.service.BacktestBatchService;
import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

/**
 * 批量回测服务实现（V5.96）。
 *
 * <p>执行模型：一批 = 一套策略参数 × N 只基金。发起时校验并插入批次 + 每基金一条 status=0 的
 * backtest_record（带 batch_id），随后把"编排任务"丢给批量线程池——编排任务再把每只基金的任务
 * 提交到同一池（4 工作线程），每完成一只：记录行写结果、批次成功/失败计数 +1（实时进度），
 * 全部完成后批次置为已完成。同一时间只允许一个运行中批次（用户拍板，发起时拦截）。
 *
 * <p>线程口径：程序化 {@code executor.execute()} 提交，不走 Spring AOP 代理（规避 @Async
 * 自调用失效）；MDC 由线程池的 TaskDecorator 透传，日志 traceId 不断链。
 *
 * <p>断点恢复：应用重启时如有"运行中"批次（进程被杀/重启导致中断），启动即把批次置为已完成、
 * 未跑完的记录行置为失败（errorMsg=应用重启中断），并按记录行实际状态重算成功/失败数——
 * 避免死批次永久占用"同时只允许一个批次"的互斥名额。
 */
@Service
public class BacktestBatchServiceImpl implements BacktestBatchService, ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BacktestBatchServiceImpl.class);

    private final BacktestBatchMapper batchMapper;

    private final BacktestRecordMapper recordMapper;

    private final FundBasicMapper fundBasicMapper;

    private final BacktestServiceImpl backtestService;

    private final StrategyRegistry registry;

    private final ThreadPoolTaskExecutor batchExecutor;

    public BacktestBatchServiceImpl(BacktestBatchMapper batchMapper, BacktestRecordMapper recordMapper,
                                    FundBasicMapper fundBasicMapper, BacktestServiceImpl backtestService,
                                    StrategyRegistry registry,
                                    @Qualifier("backtestBatchExecutor") ThreadPoolTaskExecutor batchExecutor) {
        this.batchMapper = batchMapper;
        this.recordMapper = recordMapper;
        this.fundBasicMapper = fundBasicMapper;
        this.backtestService = backtestService;
        this.registry = registry;
        this.batchExecutor = batchExecutor;
    }

    @Override
    public Long create(BatchBacktestRequest request) {
        validate(request);
        String paramsJson = JsonUtils.toJson(request.getParams() == null ? Map.of() : request.getParams());
        // 参数合法性先拦（复用策略自身的 validateParams，非法参数不建批次）
        com.quant.strategy.core.Strategy strategy = registry.getRequired(request.getStrategyType());
        strategy.validateParams(JsonUtils.mapper().readTree(paramsJson));

        // 互斥：同一时间只允许一个运行中批次（用户拍板）
        Long running = batchMapper.selectCount(new LambdaQueryWrapper<BacktestBatch>()
                .eq(BacktestBatch::getStatus, BacktestBatch.STATUS_RUNNING));
        if (running != null && running > 0) {
            throw new BizException("已有运行中的批量回测批次，请等它完成后再发起（批次列表可查看进度）");
        }

        // 基金必须都在自选池（去重、保序）
        List<String> fundCodes = new ArrayList<>(new LinkedHashSet<>(request.getFundCodes()));
        List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                .in(FundBasic::getFundCode, fundCodes)
                .eq(FundBasic::getStatus, 1));
        Map<String, FundBasic> fundByCode = funds.stream()
                .collect(Collectors.toMap(FundBasic::getFundCode, Function.identity(), (a, b) -> a));
        List<String> missing = fundCodes.stream().filter(code -> !fundByCode.containsKey(code)).toList();
        if (!missing.isEmpty()) {
            throw new BizException("以下基金不在自选池，无法回测：" + String.join("、", missing));
        }

        BacktestBatch batch = new BacktestBatch();
        batch.setStrategyType(request.getStrategyType());
        batch.setParams(paramsJson);
        batch.setStartDate(request.getStartDate());
        batch.setEndDate(request.getEndDate());
        batch.setInitialCapital(request.getInitialCapital());
        batch.setTotalCount(fundCodes.size());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batch.setStatus(BacktestBatch.STATUS_RUNNING);
        batch.setCreatedBy(currentUserLabel());
        batchMapper.insert(batch);

        // 每只基金一条记录行（status=0），统一初始资金模式下先写死；自动模式留给任务内自算。
        // 开始日期按基金自动修正（V5.97 用户口径）：成立日期晚于批次开始 → 有效开始 = 成立日期 + 策略预热天数
        //（warmupDaysOf 已按"最慢均线 ×1.6 + 30"启发式覆盖各策略的回看需求）；修正后距结束不足一年 →
        // 该基金直接建成失败记录（不进执行队列），失败原因写明有效区间。
        JsonNode paramsNode = JsonUtils.mapper().readTree(paramsJson);
        List<BacktestRecord> records = new ArrayList<>(fundCodes.size());
        int preFailed = 0;
        for (String code : fundCodes) {
            LocalDate effStart = request.getStartDate();
            LocalDate inception = fundByCode.get(code).getInceptionDate();
            if (inception != null && inception.isAfter(effStart)) {
                effStart = inception.plusDays(backtestService.warmupDays(request.getStrategyType(), paramsNode));
            }
            BacktestRecord record = new BacktestRecord();
            record.setFundCode(code);
            record.setStrategyType(request.getStrategyType());
            record.setParams(paramsJson);
            record.setStartDate(effStart);
            record.setEndDate(request.getEndDate());
            record.setInitialCapital(request.getInitialCapital());
            record.setBatchId(batch.getId());
            record.setTradeCount(0);
            if (effStart.plusYears(1).isAfter(request.getEndDate())) {
                // 有效区间不足一年（成立太晚或预热太长）：不执行，直接记失败（与"开始/结束最少相隔一年"的用户口径一致）
                record.setStatus(BacktestRecord.STATUS_FAILED);
                record.setErrorMsg("成立日期[" + inception + "]叠加策略预热后，有效开始[" + effStart
                        + "]距结束[" + request.getEndDate() + "]不足一年，无法回测；请调整区间或去掉该基金");
                recordMapper.insert(record);
                preFailed++;
                LOGGER.info("批量回测批次[{}]基金[{}]区间不足一年跳过：有效开始 {}", batch.getId(), code, effStart);
                continue;
            }
            record.setStatus(BacktestRecord.STATUS_RUNNING);
            recordMapper.insert(record);
            records.add(record);
        }
        if (preFailed > 0) {
            batchMapper.update(null, new LambdaUpdateWrapper<BacktestBatch>()
                    .eq(BacktestBatch::getId, batch.getId())
                    .setSql("fail_count = fail_count + " + preFailed));
        }

        LOGGER.info("批量回测批次[{}]已发起：策略 {}，基金 {} 只，区间 {} ~ {}，初始资金 {}",
                batch.getId(), request.getStrategyType(), fundCodes.size(),
                request.getStartDate(), request.getEndDate(),
                request.getInitialCapital() == null ? "按基金自动" : request.getInitialCapital());
        batchExecutor.execute(() -> runBatch(batch.getId(), records));
        return batch.getId();
    }

    /**
     * 批次编排：提交每只基金的任务并等待全部完成，最后把批次置为已完成。
     * 在批量线程池里跑（占 1 线程做等待，其余 3 个工作线程并行回测；队列中的任务随后补位）。
     */
    private void runBatch(Long batchId, List<BacktestRecord> records) {
        CountDownLatch latch = new CountDownLatch(records.size());
        AtomicInteger remaining = new AtomicInteger(records.size());
        for (BacktestRecord record : records) {
            batchExecutor.execute(() -> {
                boolean success = false;
                try {
                    if (record.getInitialCapital() == null) {
                        // 自动模式：按基金算初始资金并回写记录行（口径见 BacktestServiceImpl.computeInitialCapital）
                        java.math.BigDecimal capital = backtestService.computeInitialCapital(
                                record.getFundCode(), record.getStrategyType(),
                                JsonUtils.mapper().readTree(record.getParams()), record.getStartDate());
                        record.setInitialCapital(capital);
                        BacktestRecord capUpdate = new BacktestRecord();
                        capUpdate.setId(record.getId());
                        capUpdate.setInitialCapital(capital);
                        recordMapper.updateById(capUpdate);
                    }
                    success = backtestService.runOne(record);
                } catch (Exception e) {
                    LOGGER.error("批量回测批次[{}]基金[{}]任务异常", batchId, record.getFundCode(), e);
                    markFailed(record.getId(), e.getMessage() == null ? "任务异常" : e.getMessage());
                } finally {
                    finishOne(batchId, success, remaining);
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("批量回测批次[{}]编排线程被中断", batchId);
        }
        BacktestBatch done = new BacktestBatch();
        done.setId(batchId);
        done.setStatus(BacktestBatch.STATUS_FINISHED);
        done.setFinishedAt(LocalDateTime.now());
        batchMapper.updateById(done);
        LOGGER.info("批量回测批次[{}]全部完成", batchId);
    }

    /** 批内单只收尾：批次成功/失败计数 +1（SQL 自增防并发丢计数），全部跑完不在此置状态（编排线程统一置）。 */
    private void finishOne(Long batchId, boolean success, AtomicInteger remaining) {
        LambdaUpdateWrapper<BacktestBatch> uw = new LambdaUpdateWrapper<BacktestBatch>()
                .eq(BacktestBatch::getId, batchId)
                .setSql(success ? "success_count = success_count + 1" : "fail_count = fail_count + 1");
        batchMapper.update(null, uw);
        int left = remaining.decrementAndGet();
        LOGGER.info("批量回测批次[{}]进度：剩 {} 只", batchId, left);
    }

    /** 把记录行标成失败（自动算初始资金失败等引擎外的异常路径）。 */
    private void markFailed(Long recordId, String message) {
        BacktestRecord fail = new BacktestRecord();
        fail.setId(recordId);
        fail.setStatus(BacktestRecord.STATUS_FAILED);
        fail.setErrorMsg(message.length() > 500 ? message.substring(0, 500) : message);
        recordMapper.updateById(fail);
    }

    @Override
    public PageResult<BacktestBatch> page(long page, long size, String strategyType) {
        IPage<BacktestBatch> result = batchMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BacktestBatch>()
                        .eq(strategyType != null && !strategyType.isBlank(), BacktestBatch::getStrategyType, strategyType)
                        .orderByDesc(BacktestBatch::getId));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public BacktestBatchDetailVO detail(Long id) {
        BacktestBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BizException("批量回测批次不存在");
        }
        BacktestBatchDetailVO vo = new BacktestBatchDetailVO();
        vo.setBatch(batch);
        vo.setRecords(recordMapper.selectList(new LambdaQueryWrapper<BacktestRecord>()
                .eq(BacktestRecord::getBatchId, id)
                .orderByAsc(BacktestRecord::getFundCode)
                .select(BacktestRecord.class, field -> !field.getColumn().contains("curve"))));
        return vo;
    }

    /**
     * 启动恢复（V5.96）：进程重启会留下"运行中"批次与 status=0 的批内记录——启动即按记录行实际
     * 状态重算成功/失败数、批次置为已完成，未跑完的记录行置为失败，释放互斥名额。
     */
    @Override
    public void run(ApplicationArguments args) {
        List<BacktestBatch> running = batchMapper.selectList(new LambdaQueryWrapper<BacktestBatch>()
                .eq(BacktestBatch::getStatus, BacktestBatch.STATUS_RUNNING));
        for (BacktestBatch batch : running) {
            List<BacktestRecord> stuck = recordMapper.selectList(new LambdaQueryWrapper<BacktestRecord>()
                    .eq(BacktestRecord::getBatchId, batch.getId()));
            int success = 0;
            int fail = 0;
            for (BacktestRecord record : stuck) {
                if (BacktestRecord.STATUS_RUNNING == record.getStatus()) {
                    markFailed(record.getId(), "应用重启，批量回测中断");
                    fail++;
                } else if (BacktestRecord.STATUS_SUCCESS == record.getStatus()) {
                    success++;
                } else {
                    fail++;
                }
            }
            BacktestBatch done = new BacktestBatch();
            done.setId(batch.getId());
            done.setSuccessCount(success);
            done.setFailCount(fail);
            done.setStatus(BacktestBatch.STATUS_FINISHED);
            done.setFinishedAt(LocalDateTime.now());
            batchMapper.updateById(done);
            LOGGER.warn("批量回测批次[{}]在上次运行中被中断，已按记录实况收尾（成功 {} / 失败 {}）",
                    batch.getId(), success, fail);
        }
    }

    /** 发起请求的基础校验（基金存在性/互斥在 create 内做）。 */
    private void validate(BatchBacktestRequest request) {
        if (request.getStrategyType() == null || request.getStartDate() == null || request.getEndDate() == null
                || request.getFundCodes() == null || request.getFundCodes().isEmpty()) {
            throw new BizException("批量回测参数不完整（策略/区间/基金列表必填）");
        }
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BizException("结束日期须晚于开始日期");
        }
        if (request.getInitialCapital() != null && request.getInitialCapital().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BizException("统一初始资金须大于 0（留空则按基金自动计算）");
        }
    }

    /** 操作账号：登录会话里的用户名（V5.96 登录时写入），旧会话取不到回退用户 ID。 */
    private String currentUserLabel() {
        try {
            Object name = StpUtil.getSession().get("username");
            return name == null ? String.valueOf(StpUtil.getLoginIdAsLong()) : String.valueOf(name);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
