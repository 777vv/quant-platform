package com.quant.ai.controller;

import java.util.List;

import com.quant.common.result.R;
import com.quant.ai.dto.AiModelConfigRequest;
import com.quant.ai.dto.AiModelConfigVO;
import com.quant.ai.dto.AiModelOptionVO;
import com.quant.ai.dto.AiProviderVO;
import com.quant.ai.service.AiModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型配置接口（平台配置）。
 *
 * <p>V4.0：**每个厂商一行**配置（Base URL / 模型 / Token / 单价），保存某厂商不会覆盖别家；
 * 「保存并生效」同时把"当前启用厂商"指向它，所以在页面上"选中厂商 + 保存"就是切换厂商。
 * 查询接口只回传打码后的 Token。
 */
@RestController
@RequestMapping("/api/ai/model-config")
public class AiModelConfigController {

    private final AiModelConfigService configService;

    public AiModelConfigController(AiModelConfigService configService) {
        this.configService = configService;
    }

    /**
     * 某个厂商的配置（Token 打码；每个厂商一行，互不覆盖）。
     *
     * @param provider 厂商代码；不传 = 当前启用的厂商
     */
    @GetMapping
    public R<AiModelConfigVO> view(@RequestParam(required = false) String provider) {
        return R.ok(configService.view(provider));
    }

    /** 可选厂商清单（含默认 Base URL 与已知免费模型） */
    @GetMapping("/providers")
    public R<List<AiProviderVO>> providers() {
        return R.ok(configService.providers());
    }

    /**
     * 拉取模型列表（GET {baseUrl}/models，OpenAI 兼容）。
     *
     * @param baseUrl  端点；为空用当前配置
     * @param apiKey   Token；为空用当前配置（尚未保存时把输入框里的值传进来即可）
     * @param provider 厂商代码，用于标注免费模型
     */
    @GetMapping("/models")
    public R<List<AiModelOptionVO>> models(@RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String provider) {
        return R.ok(configService.models(baseUrl, apiKey, provider));
    }

    /** 保存配置（保存即生效；apiKey 留空或回传打码值表示不修改已有 Token） */
    @PutMapping
    public R<Void> save(@RequestBody AiModelConfigRequest request) {
        configService.save(request);
        return R.ok();
    }

    /**
     * 连通性自检（可在保存前先测；未保存时把表单里的值直接传进来）。
     *
     * @param request 与保存同构的请求体：provider 不参与自检，baseUrl/apiKey/model 为空时用库内当前配置
     */
    @PostMapping("/test")
    public R<String> test(@RequestBody AiModelConfigRequest request) {
        return R.ok(configService.test(request.baseUrl(), request.apiKey(), request.model()));
    }
}
