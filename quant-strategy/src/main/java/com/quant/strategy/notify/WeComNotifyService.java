package com.quant.strategy.notify;

import java.util.List;

import com.quant.strategy.entity.SignalRecord;

/**
 * 微信通知服务（V5.20，方案①：企业微信自建应用 + 微信插件 → 消息直达个人微信）。
 *
 * <p>与邮件通知并行、互不影响：微信发送失败只记日志，绝不拖累邮件；触发点与邮件相同
 * （每日信号计算任务之后，仅对 BUY/SELL 提示）。Secret 打码规则与邮件授权码一致。
 */
public interface WeComNotifyService {

    /**
     * 微信通知配置视图（平台配置卡片展示；secret 不回传，仅返回是否已配置）。
     */
    WeComConfigVO config();

    /**
     * 保存微信通知配置（保存即生效，无需重启）。
     *
     * <p>secret 语义与邮件授权码一致：留空或回传打码值表示不修改已存 Secret。
     *
     * @param request 保存请求
     */
    void saveConfig(WeComConfigRequest request);

    /**
     * 发送微信测试消息（平台配置"发送测试"按钮），失败抛业务异常并在界面提示原因。
     *
     * <p>V5.26：按"界面当前填写"测试而非只测库内已存配置——表单改了还没保存时，
     * 测试的也应是用户眼前这套参数；留空或打码的字段回退库内已存值，全程不落库。
     *
     * @param request 界面当前填写的配置（可为 null = 纯按已存配置测试）
     */
    void testSend(WeComConfigRequest request);

    /**
     * 推送仓位配置偏离告警（V5.36，fail-soft：未启用/未配置/失败只记日志）。
     *
     * @param result 完整检查结果
     * @param violations 越界的类别行
     */
    void sendAllocationAlert(com.quant.fund.dto.AllocationCheckVO result,
                             java.util.List<com.quant.fund.dto.AllocationCheckVO.Row> violations);

    /**
     * 推送当日交易信号到微信（异步，失败只记日志——微信通道绝不影响邮件通道）：
     * 仅发送 BUY/SELL 信号；一条消息容纳当日全部买卖建议。
     *
     * @param signals 当日信号计算结果（含 HOLD，方法内自行过滤）
     */
    void sendSignalDigest(List<SignalRecord> signals);
}
