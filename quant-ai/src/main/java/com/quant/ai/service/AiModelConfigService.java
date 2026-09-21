package com.quant.ai.service;

import java.util.List;

import com.quant.ai.dto.AiModelConfigRequest;
import com.quant.ai.dto.AiModelConfigVO;
import com.quant.ai.dto.AiModelOptionVO;
import com.quant.ai.dto.AiProviderVO;
import com.quant.ai.entity.AiModelConfig;

/**
 * AI 模型配置服务（**每个厂商一行**：Base URL / 模型名 / Token / 单价，V4.0）。
 *
 * <p>配置从 application.yml 迁到数据库后，本服务是**唯一读取入口**：
 * 对话、连通性自检、前端展示都经由它取快照，避免各处直接读配置文件导致口径不一致。
 *
 * <p>V4.0 语义变化：保存某厂商的配置时**只写该厂商那一行**（不再覆盖别家），
 * 并把"当前启用厂商"指向它——即"选中厂商 + 保存并生效"就是切换厂商。
 */
public interface AiModelConfigService {

    /**
     * 某个厂商的配置视图（Token 打码）。
     *
     * @param provider 厂商代码；为空表示**当前启用**的厂商
     */
    AiModelConfigVO view(String provider);

    /** 当前启用厂商的原始快照（内部使用；含明文 Token，禁止直接回传前端；未配置时返回空壳） */
    AiModelConfig snapshot();

    /**
     * 保存**指定厂商**的配置，并把它设为当前启用厂商（对话客户端立即生效，无需重启）。
     *
     * @param request 保存请求；apiKey 为空或等于该厂商打码值时表示不修改其已存 Token
     */
    void save(AiModelConfigRequest request);

    /** 可选的厂商清单（含默认 Base URL、已知免费模型，以及各厂商"已配置/当前使用"状态） */
    List<AiProviderVO> providers();

    /**
     * 拉取某厂商的可用模型列表（GET {baseUrl}/models）。
     *
     * @param baseUrl  端点；为空时取该厂商库内配置或该厂商默认值
     * @param apiKey   Token；为空时取该厂商库内配置
     * @param provider 厂商代码，用于标注免费模型
     * @return 模型列表（按名称升序）
     */
    List<AiModelOptionVO> models(String baseUrl, String apiKey, String provider);

    /**
     * 连通性自检：用给定配置（或库内配置）发一句最小请求。
     *
     * @param baseUrl 端点；为空用库内配置
     * @param apiKey  Token；为空用库内配置
     * @param model   模型；为空用库内配置
     * @return 自检结果文案（含模型耗时）
     */
    String test(String baseUrl, String apiKey, String model);
}
