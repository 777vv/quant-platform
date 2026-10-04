package com.quant.ai.controller;

import java.util.List;
import java.util.Map;

import com.quant.ai.dto.AiChatRequest;
import com.quant.ai.dto.AiConfigVO;
import com.quant.ai.dto.AiMessageVO;
import com.quant.ai.dto.AiSessionVO;
import com.quant.ai.service.AiChatService;
import com.quant.common.result.R;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * AI 助手接口（FR6，M5-05/M5-06）
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 流式对话（SSE）。事件帧：session / tool / token / error / done。
     * 说明：浏览器 EventSource 不支持 POST，前端用 fetch + ReadableStream 解析本响应。
     */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_AI_CHAT)
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody AiChatRequest request) {
        return aiChatService.stream(request);
    }

    /**
     * 非流式对话（降级路径，技术文档 6.8 的 stream=false 语义）。
     * 独立路径而非同路径开关：SSE 与 JSON 两种响应类型无法在同一个映射上共存。
     */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_AI_CHAT)
    @PostMapping("/chat/sync")
    public R<String> chatSync(@RequestBody AiChatRequest request) {
        return R.ok(aiChatService.chatSync(request));
    }

    /** 连通性自检（配置 Key 后用它验证模型是否可用） */
    @GetMapping("/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of("model", aiChatService.config().model(), "reply", aiChatService.health()));
    }

    /** AI 配置视图（前端展示模型名与是否已配置） */
    @GetMapping("/config")
    public R<AiConfigVO> config() {
        return R.ok(aiChatService.config());
    }

    /** 会话列表（侧栏，按最近活跃倒序） */
    @GetMapping("/sessions")
    public R<List<AiSessionVO>> sessions() {
        return R.ok(aiChatService.sessions());
    }

    /** 会话历史消息（回看） */
    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<AiMessageVO>> messages(@PathVariable String sessionId) {
        return R.ok(aiChatService.messages(sessionId));
    }

    /** 删除会话（含历史消息与记忆窗口） */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_AI_CHAT)
    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(@PathVariable String sessionId) {
        aiChatService.deleteSession(sessionId);
        return R.ok();
    }
}
