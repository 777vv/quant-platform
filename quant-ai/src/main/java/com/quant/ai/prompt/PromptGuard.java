package com.quant.ai.prompt;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 提示词注入护栏（FR6，M5-02 加固）。
 *
 * 背景：实测 glm-4-flash 在"复合注入"（同时要求忽略指令 + 输出提示词 + 写诗）下会出现
 * 「先按统一话术拒绝、随后仍部分照做」的让步行为，仅靠系统提示词无法完全约束小模型。
 * 因此对**明确无正常用途**的越狱话术在入口处直接拦下，返回与系统提示词一致的统一拒答，
 * 不再调用大模型——确定性、零成本、且不会随模型版本漂移。
 *
 * 护栏只覆盖四类明确的越狱家族，不做宽泛关键词过滤，避免误伤正常提问
 * （例如"帮我看看持仓""510300 估值贵不贵"都不匹配任何规则）。
 */
public final class PromptGuard {

    /** 统一拒答话术（与 SystemPrompts 中的话术保持一致，改动需同步） */
    public static final String REFUSAL = "抱歉，我只回答基金、指数、估值、策略和本平台使用相关的问题。"
            + "你可以问我持仓情况、某只基金的行情，或最近的买卖信号。";

    /** 一、指令覆盖：试图让模型忽略既有规则 */
    private static final Pattern INSTRUCTION_OVERRIDE = Pattern.compile(
            "(忽略|无视|忘记|不要理会|不用理会|不受限制|绕过)"
                    + "[^。；\\n]{0,12}(之前|前面|以上|上述|所有|全部|你的|原有)[^。；\\n]{0,8}(指令|规则|提示|设定|限制)"
                    + "|(ignore|disregard|forget)[^.]{0,24}(previous|above|prior|all)[^.]{0,24}(instruction|rule|prompt)",
            Pattern.CASE_INSENSITIVE);

    /** 二、提示词套取：索要或暗示系统提示词内容 */
    private static final Pattern PROMPT_DISCLOSURE = Pattern.compile(
            "(系统|初始|原始|你的|内置)(提示词|提示语|prompt|设定|预设|规则原?文)"
                    + "|(system|initial|original)\\s*prompt|jailbreak|越狱模式|DAN\\s*模式",
            Pattern.CASE_INSENSITIVE);

    /** 三、角色置换：要求扮演其他身份或切换人格 */
    private static final Pattern ROLE_OVERRIDE = Pattern.compile(
            "角色扮演|扮演(一位|一个|成|作)?|假设你是|假装你是|你现在是|从现在起你是"
                    + "|pretend\\s+(to\\s+be|you\\s+are)|you\\s+are\\s+now",
            Pattern.CASE_INSENSITIVE);

    /** 四、规则转写：把规则翻译/改写成别的形式（换语言、换格式同样属于泄漏） */
    private static final Pattern RULE_REWRITE = Pattern.compile(
            "(翻译|改写|转写|复述|罗列|列举|输出成|写成)[^。；\\n]{0,14}(规则|提示词|提示语|设定|条款|要求)"
                    + "|(translate|rewrite|repeat|list)[^.]{0,24}(rule|prompt|instruction|setting)",
            Pattern.CASE_INSENSITIVE);

    /** 全部越狱规则（命中任一即拦截） */
    private static final List<Pattern> GUARD_PATTERNS =
            List.of(INSTRUCTION_OVERRIDE, PROMPT_DISCLOSURE, ROLE_OVERRIDE, RULE_REWRITE);

    /**
     * 判断提问是否命中越狱话术。
     *
     * @param question 用户原始提问
     * @return true=应直接返回统一拒答，不调用模型
     */
    public static boolean isJailbreak(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return GUARD_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(question).find());
    }

    private PromptGuard() {
        // 工具类禁止实例化
    }
}
