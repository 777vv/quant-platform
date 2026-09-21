package com.quant.ai.tool;

import com.quant.ai.manual.ManualCatalog;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具七：平台使用手册（V4.2）。
 *
 * <p>为什么需要它：报价、持仓这类问题靠取数工具就够，但"**这个功能怎么用**"——回测参数什么意思、
 * 在哪配置模型与 Token、导入为什么是覆盖刷新、额度超了怎么办——属于**平台自己的说明书**，模型没有
 * 可靠记忆，凭印象回答必然编。所以把手册（`docs/05-使用手册.md`，随 jar 发布）做成一个工具按需检索。
 *
 * <p>检索与返回口径见 {@link ManualCatalog}：命中一节就返回那一节（超长按段返回 + 提示 part），
 * 没命中就回目录，**不猜**。手册正文不进系统提示词，避免每次请求都多背几万 token。
 */
@Component
public class ManualTool {

    private final ManualCatalog catalog;

    public ManualTool(ManualCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 查平台使用手册：功能怎么用、参数含义、在哪配置、出错怎么办。
     *
     * @param topic    章节或小节名（可含口语，如 回测 / 回测参数 / 导入 / 记账 / 平台配置 / 用量 / 定时任务 / 常见问题 / 术语）；不确定就留空
     * @param question 用户的原问题（topic 留空或不准时用它做关键词检索，如"回测参数是什么意思"）
     * @param part     长小节的分段号（从 1 起；返回里会提示是否需要再取下一段），一般不用填
     * @return 命中的手册小节正文（含"本章还有…"与手册章节清单，便于继续追问）
     */
    @Tool(name = "getPlatformManual", description = "查询本平台的使用手册：某个功能怎么用、在哪里操作、参数是什么意思、有哪些注意事项、报错怎么办（例如回测参数、数据导入、记账与分红、策略配置、平台配置里的模型与 Token、AI 用量与额度、定时同步、常见问题、术语口径）。用户问「怎么用/怎么弄/参数什么意思/在哪配置/为什么这样/报错了怎么办」时，先调它再回答，不要凭印象编操作步骤。")
    public String getPlatformManual(
            @ToolParam(description = "章节或小节名，可含口语，如 回测 / 回测参数 / 数据导入 / 记账 / 分红 / 策略配置 / 平台配置 / 模型 / 用量 / 定时任务 / 常见问题 / 术语；不确定就留空") String topic,
            @ToolParam(description = "用户的原问题，用于关键词检索（topic 留空或不准时必填）") String question,
            @ToolParam(description = "长小节的分段号，从 1 起；仅当上次返回提示还有后续内容时使用") Integer part) {
        return catalog.render(topic, question, part);
    }
}
