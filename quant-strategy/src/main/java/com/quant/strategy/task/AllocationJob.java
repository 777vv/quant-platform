package com.quant.strategy.task;

import java.time.LocalDateTime;

import com.quant.common.log.JobLogs;
import com.quant.fund.dto.AllocationCheckVO;
import com.quant.fund.entity.AllocationConfig;
import com.quant.fund.service.AllocationService;
import com.quant.strategy.notify.NotifyService;
import com.quant.strategy.notify.WeComNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 仓位配置检查任务（V5.36，用户口径：每周二早上 9 点执行）：
 * 把当前组合（现金/美股/A股/亚太/欧洲 占总资产比例）与平台配置的目标范围比对——
 * 全部在范围内 → 不操作、不发送；任一越界 → 发告警邮件 + 微信。
 *
 * <p>口径：检查的是**最近一次同步的数据**（不依赖当天是否交易日，节假日比对的是节前持仓，同样有效）。
 * 微信推送 fail-soft（未启用/失败只记日志），邮件失败走既有重试逻辑；两者互不影响。
 *
 * <p>日志：统一走 {@link JobLogs#call}，打印「开始执行 / 执行结束（耗时）」并用任务级 traceId
 * 串联本轮全部日志（V5.38，用户要求）。
 */
@Component
public class AllocationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllocationJob.class);

    private final AllocationService allocationService;

    private final NotifyService notifyService;

    private final WeComNotifyService wecomNotifyService;

    public AllocationJob(AllocationService allocationService, NotifyService notifyService,
                         WeComNotifyService wecomNotifyService) {
        this.allocationService = allocationService;
        this.notifyService = notifyService;
        this.wecomNotifyService = wecomNotifyService;
    }

    /** 每周二 09:00（北京时间）仓位配置检查 */
    @Scheduled(cron = "0 0 9 * * TUE", zone = "Asia/Shanghai")
    public void checkWeekly() {
        runOnce("weekly");
    }

    /**
     * 手动触发与定时任务共用的执行体（返回是否发生了告警）。
     * 统一走 {@link JobLogs#call}：打印「开始执行 / 执行结束（耗时）」+ 任务级 traceId 串联本轮日志；
     * 任务名带 trigger（weekly/manual），日志里一眼能看出是定时跑的还是点【立即检查】跑的。
     */
    public boolean runOnce(String trigger) {
        Boolean alerted = JobLogs.call("allocation:" + trigger, () -> {
            AllocationConfig config = allocationService.config();
            if (!Integer.valueOf(1).equals(config.getEnabled())) {
                LOGGER.info("仓位配置检查已停用（enabled=0），本次[{}]不执行", trigger);
                return Boolean.FALSE;
            }
            AllocationCheckVO result = allocationService.check();
            if (!result.evaluated()) {
                LOGGER.warn("总资产为 0，仓位比例无从评估，本次[{}]不发送", trigger);
                return Boolean.FALSE;
            }
            var violations = result.rows().stream().filter(row -> !row.ok()).toList();
            if (violations.isEmpty()) {
                LOGGER.info("仓位检查通过：五类占比均在配置范围内，不发送（总资产 {}）", result.totalAssets());
                return Boolean.FALSE;
            }
            LOGGER.warn("仓位偏离 {} 类，发送告警：{}", violations.size(),
                    violations.stream().map(v -> v.label() + " " + v.currentPct() + "%").toList());
            // 邮件（模板渲染，失败走既有重试）+ 微信（fail-soft），两通道互不影响
            notifyService.sendAllocationAlert(result, violations);
            wecomNotifyService.sendAllocationAlert(result, violations);
            return Boolean.TRUE;
        });
        // 执行失败时模板返回 null → 视为未告警（与原先 catch 里 return false 一致）
        return Boolean.TRUE.equals(alerted);
    }
}
