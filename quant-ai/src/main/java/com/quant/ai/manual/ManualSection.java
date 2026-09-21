package com.quant.ai.manual;

/**
 * 使用手册的一节（手册里一个 `### 小节`，检索与返回的最小单元）。
 *
 * @param chapterTitle 所属章节标题（手册里的 `## 一级标题`，如「7. 回测」）
 * @param title        小节标题（如「7.1 怎么发起一次回测」）
 * @param body         小节正文（Markdown 原文，不含标题行）
 */
public record ManualSection(
        /** 所属章节标题 */
        String chapterTitle,
        /** 小节标题 */
        String title,
        /** 小节正文（Markdown） */
        String body) {
}
