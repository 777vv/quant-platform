package com.quant.ai.service;

import java.util.List;

import com.quant.ai.dto.AiChatRequest;
import com.quant.ai.dto.AiConfigVO;
import com.quant.ai.dto.AiMessageVO;
import com.quant.ai.dto.AiSessionVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * AI 助手服务（FR6，M5-04/M5-05）
 */
public interface AiChatService {

    /**
     * 流式对话：返回 SSE 事件流（session / tool / token / error / done 五类事件帧）。
     *
     * @param request 对话请求（sessionId 为空则新建会话）
     */
    Flux<ServerSentEvent<String>> stream(AiChatRequest request);

    /**
     * 非流式对话（降级路径）：一次性返回完整回答。
     */
    String chatSync(AiChatRequest request);

    /**
     * 连通性与配置自检：发送一句极简提问，验证 Key/模型/网络是否可用。
     *
     * @return 模型原始回复
     */
    String health();

    /**
     * 会话列表（按最近活跃倒序）。
     */
    List<AiSessionVO> sessions();

    /**
     * 会话历史消息（按时间升序，供回看）。
     */
    List<AiMessageVO> messages(String sessionId);

    /**
     * 删除会话（含历史消息与记忆窗口）。
     */
    void deleteSession(String sessionId);

    /**
     * 配置视图（前端展示模型名与"是否已配置"）。
     */
    AiConfigVO config();
}
