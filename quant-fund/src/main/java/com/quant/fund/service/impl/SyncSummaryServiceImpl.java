package com.quant.fund.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.util.JsonUtils;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.service.SyncSummaryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.quant.fund.config.SyncSummaryProperties;

import tools.jackson.core.type.TypeReference;

/**
 * 同步状态汇总实现（FR1，M4-09）。
 * 新鲜度判定为近似口径：按周末推算"应达数据日期"，并留出法定节假日宽限
 * （ETF 4 个自然日、场外 7 个自然日——场外净值 T+1 公布再放宽）；
 * 超过宽限仍未追平则标记 LAGGING，提示手动补拉或检查任务日志。
 */
@Service
public class SyncSummaryServiceImpl implements SyncSummaryService {

    /** 汇总缓存的 Redis 键（值为 List&lt;SyncStatusItem&gt; JSON） */
    private static final String CACHE_KEY = "quant:sync:summary";

    /** 汇总缓存时长：25 小时（跨过每日 22:00 刷新点） */
    private static final Duration CACHE_TTL = Duration.ofHours(25);

    /** ETF 收盘数据当日定型时间（此前当日数据视为未可得） */
    private static final int ETF_FINALIZE_HOUR = 15;

    private static final int ETF_FINALIZE_MINUTE = 30;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final StringRedisTemplate redisTemplate;

    private final SyncSummaryProperties properties;

    public SyncSummaryServiceImpl(FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                                  FundNavMapper navMapper, StringRedisTemplate redisTemplate,
                                  SyncSummaryProperties properties) {
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public List<DashboardOverviewVO.SyncStatusItem> computeSummary() {
        List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getStatus, 1).orderByAsc(FundBasic::getFundCode));
        LocalDate expectedEtf = expectedEtfDate();
        LocalDate expectedOtc = previousWeekday(expectedEtf);
        List<DashboardOverviewVO.SyncStatusItem> items = new ArrayList<>(funds.size());
        for (FundBasic fund : funds) {
            LocalDate expected = FundTypeEnum.ETF.getCode() == fund.getFundType() ? expectedEtf : expectedOtc;
            int allowedLag = FundTypeEnum.ETF.getCode() == fund.getFundType()
                    ? properties.getEtfAllowedLagDays() : properties.getOtcAllowedLagDays();
            LocalDate lastData = lastDataDate(fund);
            String status = lastData == null || properties.isForceLagging()
                    ? STATUS_LAGGING
                    : lastData.isBefore(expected.minusDays(allowedLag)) ? STATUS_LAGGING : STATUS_NORMAL;
            items.add(new DashboardOverviewVO.SyncStatusItem(fund.getFundCode(), fund.getFundName(),
                    fund.getFundType(), lastData == null ? null : lastData.toString(),
                    expected.toString(), status));
        }
        redisTemplate.opsForValue().set(CACHE_KEY, JsonUtils.toJson(items), CACHE_TTL);
        return items;
    }

    @Override
    public List<DashboardOverviewVO.SyncStatusItem> summary() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        return cached == null ? computeSummary() : parseItems(cached);
    }

    /** 解析缓存 JSON（record 反序列化需带类型的 TypeReference） */
    private List<DashboardOverviewVO.SyncStatusItem> parseItems(String json) {
        return JsonUtils.mapper().readValue(json, new TypeReference<List<DashboardOverviewVO.SyncStatusItem>>() {
        });
    }

    /** ETF 应达数据日：15:30 前以上一交易日为基准，再跳过周末 */
    private LocalDate expectedEtfDate() {
        LocalDate date = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() * 60 + now.getMinute() < ETF_FINALIZE_HOUR * 60 + ETF_FINALIZE_MINUTE) {
            date = previousWeekday(date);
        }
        return skipWeekend(date);
    }

    /** 往前回退一个工作日（跳过周末，不识别法定节假日） */
    private LocalDate previousWeekday(LocalDate date) {
        return skipWeekend(date.minusDays(1));
    }

    /** 若落在周六/周日则继续向前回退到周五 */
    private LocalDate skipWeekend(LocalDate date) {
        while (date.getDayOfWeek().getValue() > 5) {
            date = date.minusDays(1);
        }
        return date;
    }

    /** 本地最新数据日期：ETF 取最大 K 线日，场外取最大净值日 */
    private LocalDate lastDataDate(FundBasic fund) {
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            FundEtfKline row = klineMapper.selectOne(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fund.getFundCode())
                    .orderByDesc(FundEtfKline::getTradeDate).last("limit 1"));
            return row == null ? null : row.getTradeDate();
        }
        FundNav row = navMapper.selectOne(new LambdaQueryWrapper<FundNav>()
                .eq(FundNav::getFundCode, fund.getFundCode())
                .orderByDesc(FundNav::getNavDate).last("limit 1"));
        return row == null ? null : row.getNavDate();
    }
}
