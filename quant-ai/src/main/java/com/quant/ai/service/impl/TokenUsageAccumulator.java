package com.quant.ai.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 一次咨询的 token 累加器（V3.9）。
 *
 * <p>为什么需要它：一次咨询可能对应**多次上游请求**——工具调用的每一轮都是一次完整的
 * completion（系统提示词与记忆窗口都会重发），所以 token 必须**累加**，不能只取最后一帧。
 *
 * <p>累加口径（先取证再定论，实测见 docs/02 V3.9）：
 * 1. 上游在一次请求结束时**只在一帧**回传 usage（实测 DEEPSEEK 流式 18 帧中恰 1 帧），
 *    且该帧 `id` 与同请求其余帧一致 → **按 id 归并，同一 id 取最后一次非空取值**；
 * 2. 上游没给 id 时退化为「相邻两次取值完全相同即视为同一请求的重复帧」——真·多轮请求之间
 *    输入必然不同（工具结果会追加进上下文），只有重复帧才可能取到完全相同的三元组；
 * 3. 全程未回 usage 时由 {@link #estimateFromChars(int, int)} 兜底估算并置 `estimated` 标记，
 *    界面标注「估算」——宁可标注，也不假装精确。
 */
final class TokenUsageAccumulator {

    /** 单次上游请求的 token 三元组 */
    private record Tokens(int prompt, int cached, int completion) {

        /** 逐项相加（用于把多轮请求汇总成一个总数） */
        Tokens plus(Tokens other) {
            return new Tokens(prompt + other.prompt, cached + other.cached, completion + other.completion);
        }
    }

    /** 每个上游请求一个槽位（下标 = 请求先后顺序），同一 id 的多帧写入同一槽位 */
    private final List<Tokens> slots = new ArrayList<>();

    /** 上游请求 id → 槽位下标 */
    private final Map<String, Integer> slotById = new HashMap<>();

    /** 全程无 usage 时的字符估算值（上游真正回 usage 时不会被采用） */
    private Tokens estimatedSlot;

    /**
     * 收下一帧响应：只有带 usage 的帧才记账（同一次请求的重复帧只保留最后一次取值）。
     *
     * @param response 单帧响应（可为 null，安全忽略）
     */
    void accept(ChatResponse response) {
        Tokens tokens = tokensOf(response);
        if (tokens == null) {
            return;
        }
        String id = idOf(response);
        Integer index = id == null ? null : slotById.get(id);
        if (index != null) {
            slots.set(index, tokens);
            return;
        }
        if (!slots.isEmpty() && slots.get(slots.size() - 1).equals(tokens)) {
            // 相邻同值：同一次请求的重复帧，忽略（否则会把一次请求算成多次）
            return;
        }
        slots.add(tokens);
        if (id != null) {
            slotById.put(id, slots.size() - 1);
        }
    }

    /**
     * 兜底估算：上游全程未回 usage 时按字符数折算。
     *
     * <p>按「1 字 ≈ 1 token」**取高**（中文实测约 0.6~0.7，取高是宁高勿低，避免额度被低估）；
     * 输入只算提问与系统提示词、**不含记忆窗口历史**（无 usage 时无从得知），故估算整体仍可能偏低。
     *
     * @param inputChars  输入字符数（提问 + 系统提示词）
     * @param outputChars 输出字符数（回答正文）
     */
    void estimateFromChars(int inputChars, int outputChars) {
        this.estimatedSlot = new Tokens(Math.max(inputChars, 0), 0, Math.max(outputChars, 0));
    }

    /** 上游请求次数（工具调用会大于 1；走估算兜底时按 1 次计） */
    int rounds() {
        return slots.isEmpty() ? 1 : slots.size();
    }

    /** 输入 token（多轮之和） */
    int promptTokens() {
        return sum().prompt();
    }

    /** 输入中命中缓存的 token（多轮之和） */
    int cachedTokens() {
        return sum().cached();
    }

    /** 输出 token（多轮之和） */
    int completionTokens() {
        return sum().completion();
    }

    /** 总 token（输入 + 输出） */
    int totalTokens() {
        Tokens total = sum();
        return total.prompt() + total.completion();
    }

    /** 是否走了字符估算兜底（上游一帧 usage 都没回） */
    boolean estimated() {
        return slots.isEmpty() && estimatedSlot != null;
    }

    /** 汇总：有 usage 用 usage，否则用估算值，两者皆无则为 0 */
    private Tokens sum() {
        if (slots.isEmpty()) {
            return estimatedSlot == null ? new Tokens(0, 0, 0) : estimatedSlot;
        }
        Tokens total = new Tokens(0, 0, 0);
        for (Tokens slot : slots) {
            total = total.plus(slot);
        }
        return total;
    }

    /** 取单帧 usage（元数据/usage/总 token 任一缺失都返回 null，视为该帧没有用量信息） */
    private Tokens tokensOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return null;
        }
        int prompt = number(usage.getPromptTokens());
        int completion = number(usage.getCompletionTokens());
        long cacheRead = usage.getCacheReadInputTokens() == null ? 0L : usage.getCacheReadInputTokens();
        // 命中缓存的部分是输入 token 的子集：上游若报得比输入还大，按输入封顶
        int cached = (int) Math.min(cacheRead, prompt);
        return new Tokens(prompt, cached, completion);
    }

    /** 取上游请求 id（把同一次请求的多帧归到同一槽位） */
    private String idOf(ChatResponse response) {
        String id = response.getMetadata() == null ? null : response.getMetadata().getId();
        return id == null || id.isBlank() ? null : id;
    }

    /** Integer → int（null 视作 0） */
    private int number(Integer value) {
        return value == null ? 0 : value;
    }
}
