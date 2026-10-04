package com.quant.strategy.controller;

import java.util.List;

import com.quant.common.result.R;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.service.SyncSummaryService;
import com.quant.strategy.notify.MailConfigRequest;
import com.quant.strategy.notify.MailConfigVO;
import com.quant.strategy.notify.NotifyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.quant.common.auth.PermissionCodes;

/**
 * 邮件通知接口（FR4，M4-04；V4.9 配置入库）：平台配置邮件卡（查看/保存 SMTP 配置 + 测试发送）
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

    /** 邮件配置视图（账号脱敏；V4.9 起可编辑，保存走 PUT） */
    @GetMapping("/config")
    public R<MailConfigVO> config() {
        return R.ok(notifyService.mailConfig());
    }

    /**
     * 保存邮件配置（V4.9：SMTP 参数与开关入库，保存即生效无需重启）。
     *
     * @param request 配置请求；password 留空或回传打码值表示不修改已存授权码
     */
    @SaCheckRole(com.quant.common.auth.PermissionCodes.ROLE_ADMIN)
    @PutMapping("/config")
    public R<Void> save(@Valid @RequestBody MailConfigRequest request) {
        notifyService.saveMailConfig(request);
        return R.ok();
    }

    /**
     * 发送测试邮件（失败以业务异常返回具体原因）。
     *
     * <p>V5.26：请求体可空；带上时按"界面当前填写"测试（改了没保存也能测眼前这套），
     * 留空/打码字段回退库内已存值，全程不落库。
     *
     * @param request 界面当前填写的邮件配置（可为 null）
     */
    @SaCheckRole(com.quant.common.auth.PermissionCodes.ROLE_ADMIN)
    @PostMapping("/test")
    public R<Void> test(@RequestBody(required = false) MailConfigRequest request) {
        notifyService.sendTestMail(request);
        return R.ok();
    }

    /** 立即补发未通知的买卖信号摘要（返回纳入邮件的条数；重复点击不会重复发信） */
    @SaCheckRole(com.quant.common.auth.PermissionCodes.ROLE_ADMIN)
    @PostMapping("/digest")
    public R<Integer> digest() {
        return R.ok(notifyService.sendPendingDigest());
    }

    /**
     * 立即检查数据同步状态并对滞后基金发送告警邮件（返回滞后基金清单）。
     * 与每日 22:05 任务同一判定口径，供演练与故障排查使用。
     */
    @SaCheckRole(com.quant.common.auth.PermissionCodes.ROLE_ADMIN)
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
