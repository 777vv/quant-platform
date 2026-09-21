package com.quant.strategy.notify;

import java.util.List;

import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.strategy.entity.SignalRecord;

/**
 * 邮件通知服务（FR4/FR5，M4-04）：测试邮件、每日信号摘要与同步异常告警
 */
public interface NotifyService {

    /**
     * 发送测试邮件（平台配置"发送测试邮件"按钮），失败抛业务异常并在界面提示原因。
     */
    void sendTestMail();

    /**
     * 发送每日信号摘要邮件（异步，失败自动重试 2 次）：
     * 开关关闭时静默跳过；仅发送 BUY/SELL 且未通知的信号，发送成功后将对应信号标记为已通知。
     *
     * @param signals 当日信号计算结果（含 HOLD）
     */
    void sendSignalDigest(List<SignalRecord> signals);

    /**
     * 立即补发"仍未通知的买卖信号"摘要（同步执行，便于界面即时反馈）。
     * 与每日 21:00 任务走同一发送路径与同一去重标记，故重复点击不会重复发信。
     *
     * @return 本次实际纳入邮件的信号条数；0 表示没有待通知信号
     */
    int sendPendingDigest();

    /**
     * 发送数据同步异常告警邮件（异步，失败自动重试 2 次）：
     * 仅当存在滞后基金且通知开关开启时发送。
     *
     * @param lagging 状态为 LAGGING 的基金清单
     */
    void sendSyncAlert(List<DashboardOverviewVO.SyncStatusItem> lagging);

    /**
     * 邮件配置视图（平台配置卡片展示，账号脱敏；V4.9 起配置入库）。
     */
    MailConfigVO mailConfig();

    /**
     * 保存邮件配置（V4.9：SMTP 参数与开关入库，保存即生效，无需重启）。
     *
     * <p>password 语义与 AI 模型配置的 Token 一致：留空或回传打码值表示不修改已存授权码。
     *
     * @param request 保存请求
     */
    void saveMailConfig(MailConfigRequest request);
}
