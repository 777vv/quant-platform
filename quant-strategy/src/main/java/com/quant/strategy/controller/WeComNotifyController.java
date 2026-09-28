package com.quant.strategy.controller;

import com.quant.common.result.R;
import com.quant.strategy.notify.WeComConfigRequest;
import com.quant.strategy.notify.WeComConfigVO;
import com.quant.strategy.notify.WeComNotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信通知接口（V5.20，企业微信自建应用 + 微信插件 → 消息直达个人微信；与邮件通道并行、互不影响）
 */
@RestController
@RequestMapping("/api/notify/wecom")
public class WeComNotifyController {

    private final WeComNotifyService wecomNotifyService;

    public WeComNotifyController(WeComNotifyService wecomNotifyService) {
        this.wecomNotifyService = wecomNotifyService;
    }

    /** 微信通知配置视图（Secret 打码不回传；configured=关键配置是否齐全） */
    @GetMapping("/config")
    public R<WeComConfigVO> config() {
        return R.ok(wecomNotifyService.config());
    }

    /**
     * 保存微信通知配置（保存即生效；secret 留空/打码 = 保持原值）。
     * 返回**保存后的配置视图**：前端据此判断是否已配置齐全（防止漏传字段却提示"保存成功"）。
     */
    @PutMapping("/config")
    public R<WeComConfigVO> saveConfig(@RequestBody WeComConfigRequest request) {
        wecomNotifyService.saveConfig(request);
        return R.ok(wecomNotifyService.config());
    }

    /**
     * 发送微信测试消息（失败以业务异常返回具体原因，便于在界面排查）。
     *
     * <p>V5.26：请求体可空；带上时按"界面当前填写"测试（改了没保存也能测眼前这套），
     * 留空/打码字段回退库内已存值，全程不落库。
     *
     * @param request 界面当前填写的微信配置（可为 null）
     */
    @PostMapping("/test")
    public R<Void> test(@RequestBody(required = false) WeComConfigRequest request) {
        wecomNotifyService.testSend(request);
        return R.ok();
    }
}
