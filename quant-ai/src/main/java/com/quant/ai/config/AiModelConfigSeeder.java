package com.quant.ai.config;

import com.quant.ai.entity.AiModelConfig;
import com.quant.ai.entity.AiRuntimeConfig;
import com.quant.ai.enums.AiProviderEnum;
import com.quant.ai.mapper.AiModelConfigMapper;
import com.quant.ai.mapper.AiRuntimeConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 一次性迁移：把 application.yml / 环境变量里的旧模型配置导入数据库（作为**智谱那一行**并设为当前厂商）。
 *
 * <p>仅在"库内还没有任何厂商配置"且"配置文件里确实给了 Key"时执行，导入后模型配置的唯一来源就是数据库
 * （平台配置维护）；配置文件里的 Key 可以随即清空/删除，代码仓库不再保留密钥。
 *
 * <p>V4.0 起配置按厂商分行，故这里写 ai_model_config 的 ZHIPU 行 + 把 ai_runtime_config.active_provider
 * 指向它。
 */
@Component
public class AiModelConfigSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiModelConfigSeeder.class);

    /** 单行运行时配置的固定主键 */
    private static final Long RUNTIME_ID = 1L;

    private final AiModelConfigMapper configMapper;

    private final AiRuntimeConfigMapper runtimeMapper;

    /** 旧配置：Key（建议经环境变量 ZHIPU_API_KEY 提供） */
    @Value("${spring.ai.openai.api-key:}")
    private String legacyApiKey;

    /** 旧配置：Base URL */
    @Value("${spring.ai.openai.base-url:}")
    private String legacyBaseUrl;

    /** 旧配置：模型名 */
    @Value("${spring.ai.openai.chat.options.model:}")
    private String legacyModel;

    public AiModelConfigSeeder(AiModelConfigMapper configMapper, AiRuntimeConfigMapper runtimeMapper) {
        this.configMapper = configMapper;
        this.runtimeMapper = runtimeMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (legacyApiKey == null || legacyApiKey.isBlank()) {
            return;
        }
        if (configMapper.selectCount(null) > 0) {
            // 库里已有厂商配置，不覆盖用户后来在页面上的修改
            return;
        }
        AiModelConfig config = new AiModelConfig();
        config.setProvider(AiProviderEnum.ZHIPU.name());
        config.setBaseUrl(legacyBaseUrl == null || legacyBaseUrl.isBlank()
                ? AiProviderEnum.ZHIPU.getDefaultBaseUrl() : legacyBaseUrl.trim());
        config.setModel(legacyModel == null || legacyModel.isBlank() ? "glm-4-flash" : legacyModel.trim());
        config.setApiKey(legacyApiKey.trim());
        configMapper.insert(config);
        // 当前启用厂商指向刚导入的智谱（单行表缺行则补建）
        AiRuntimeConfig runtime = runtimeMapper.selectById(RUNTIME_ID);
        AiRuntimeConfig update = new AiRuntimeConfig();
        update.setId(RUNTIME_ID);
        update.setActiveProvider(AiProviderEnum.ZHIPU.name());
        if (runtime == null) {
            runtimeMapper.insert(update);
        } else {
            runtimeMapper.updateById(update);
        }
        LOGGER.info("已将配置文件中的旧模型配置导入数据库（厂商 智谱，模型 {}），后续请在【平台配置 → AI 模型配置】维护",
                config.getModel());
    }
}
