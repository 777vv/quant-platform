package com.quant.ai.enums;

import java.util.List;
import java.util.Set;

/**
 * 支持的模型厂商（均为 OpenAI 兼容端点，故同一套客户端即可对接）。
 *
 * <p>Base URL 为各家官方 OpenAI 兼容地址，可在平台配置按需修改；
 * {@link #getFreeModels()} 是**内置的已知免费模型参考清单**——各家定价与免费额度会变，
 * 仅用于在模型列表里给免费档加标注，最终以厂商官网为准（清单可直接在本枚举里增删）。
 */
public enum AiProviderEnum {

    /** 智谱 GLM（glm-4-flash / glm-4v-flash 为官方免费档） */
    ZHIPU("智谱", "https://open.bigmodel.cn/api/paas/v4", Set.of("glm-4-flash", "glm-4v-flash")),

    /** 阿里云百炼（通义千问） */
    QWEN("千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", Set.of()),

    /** DeepSeek */
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", Set.of()),

    /** 月之暗面 Kimi */
    KIMI("Kimi", "https://api.moonshot.cn/v1", Set.of()),

    /** MiniMax */
    MINIMAX("MiniMax", "https://api.minimax.chat/v1", Set.of());

    /** 厂商展示名 */
    private final String displayName;

    /** OpenAI 兼容端点默认值 */
    private final String defaultBaseUrl;

    /** 已知免费模型（大小写不敏感匹配） */
    private final Set<String> freeModels;

    AiProviderEnum(String displayName, String defaultBaseUrl, Set<String> freeModels) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.freeModels = freeModels;
    }

    /** 按代码解析厂商，未知代码回退智谱（兼容历史配置） */
    public static AiProviderEnum of(String code) {
        if (code == null || code.isBlank()) {
            return ZHIPU;
        }
        for (AiProviderEnum provider : values()) {
            if (provider.name().equalsIgnoreCase(code.trim())) {
                return provider;
            }
        }
        return ZHIPU;
    }

    /** 是否属于已知免费模型 */
    public boolean isFreeModel(String model) {
        return model != null && freeModels.stream().anyMatch(free -> free.equalsIgnoreCase(model.trim()));
    }

    /** 该厂商未知（列表为空表示没有内置免费清单，不代表一定收费） */
    public List<String> freeModelList() {
        return freeModels.stream().toList();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public Set<String> getFreeModels() {
        return freeModels;
    }
}
