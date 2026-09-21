package com.quant.strategy.controller;

import java.util.List;

import com.quant.common.result.R;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.service.SyncSummaryService;
import com.quant.strategy.notify.MailConfigVO;
import com.quant.strategy.notify.NotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件通知接口（FR4，M4-04）：平台配置邮件卡片（配置只读 + 测试发送）
 */
@RestController
@RequestMapping("/api/notify/mail")
public class NotifyController {

    private final NotifyService notifyService;

    private final SyncSummaryService syncSummaryService;

    public NotifyController(NotifyService notifyService, SyncSummaryService syncSummaryService) {
        this.notifyService = notifyService;
        this.syncSummaryService = syncSummaryService;
    }

    /** 邮件配置只读视图（账号脱敏） */
    @GetMapping("/config")
    public R<MailConfigVO> config() {
        return R.ok(notifyService.mailConfig());
    }

    /** 发送测试邮件（失败以业务异常返回具体原因） */
    @PostMapping("/test")
    public R<Void> test() {
        notifyService.sendTestMail();
        return R.ok();
    }

    /** 立即补发未通知的买卖信号摘要（返回纳入邮件的条数；重复点击不会重复发信） */
    @PostMapping("/digest")
    public R<Integer> digest() {
        return R.ok(notifyService.sendPendingDigest());
    }

    /**
     * 立即检查数据同步状态并对滞后基金发送告警邮件（返回滞后基金清单）。
     * 与每日 22:05 任务同一判定口径，供演练与故障排查使用。
     */
    @PostMapping("/sync-check")
    public R<List<DashboardOverviewVO.SyncStatusItem>> syncCheck() {
        List<DashboardOverviewVO.SyncStatusItem> lagging = syncSummaryService.computeSummary().stream()
                .filter(item -> SyncSummaryService.STATUS_LAGGING.equals(item.status()))
                .toList();
        if (!lagging.isEmpty()) {
            notifyService.sendSyncAlert(lagging);
        }
        return R.ok(lagging);
    }
}
