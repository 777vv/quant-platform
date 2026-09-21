package com.quant.ai.manual;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.quant.common.exception.BizException;

/**
 * 平台使用手册目录（V4.2）：把一份 Markdown 手册（`docs/05-使用手册.md`，构建期拷进 classpath 的 `ai/`）
 * 解析成「章节 → 小节」，供 AI 的手册工具与【使用手册】页共用。
 *
 * <p>为什么要它：AI 要能回答"这个功能怎么用、参数什么意思、报错怎么办"。手册正文**不塞进系统提示词**
 * （那会让每次请求都多背几万 token），而是按需检索、只把命中的那一节给模型。
 *
 * <p>检索口径（中文没有空格分词，刻意不引分词器/向量库）：
 * 把查询串（topic + question）切成**相邻两字组**，统计各小节里命中的词数——
 * 章节标题×3、小节标题×5、正文×1，取分最高者（同分取靠前的小节，保证确定性）；
 * 一个都没命中就返回 null，由调用方回目录，**不猜**。
 */
@Component
public class ManualCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManualCatalog.class);

    /** 随 jar 发布的手册资源（构建期由 docs/05-使用手册.md 拷入，见 quant-ai/pom.xml） */
    private static final String RESOURCE = "/ai/05-使用手册.md";

    /** 单次返回正文的字符上限；超出按行切段，用 part 参数取后续 */
    private static final int MAX_CHARS = 1800;

    /** 章节标题命中权重 */
    private static final int CHAPTER_TITLE_WEIGHT = 3;

    /** 小节标题命中权重（标题最能说明这一节讲什么） */
    private static final int SECTION_TITLE_WEIGHT = 5;

    /** 口语 → 手册用词的同义替换（只补高价值的几个，不做完整分词） */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("密钥", "Token"),
            Map.entry("key", "Token"),
            Map.entry("apikey", "Token"),
            Map.entry("api key", "Token"),
            Map.entry("大模型", "模型"),
            Map.entry("llm", "模型"),
            Map.entry("限额", "额度"),
            Map.entry("上限", "额度"),
            Map.entry("cost", "费用"),
            Map.entry("花钱", "费用"),
            Map.entry("花销", "费用"),
            Map.entry("派息", "分红"),
            Map.entry("说明书", "手册"),
            Map.entry("help", "手册"),
            Map.entry("报错", "失败"),
            Map.entry("crash", "失败"));

    /** 手册全文（供【使用手册】页渲染；未加载成功时为空串） */
    private final String markdown;

    /** 全部小节（按手册顺序） */
    private final List<ManualSection> sections;

    /** 章节标题清单（工具返回时用来提示"还能问什么"） */
    private final String chapterToc;

    public ManualCatalog() {
        String text = load();
        this.markdown = text;
        this.sections = parse(text);
        this.chapterToc = buildChapterToc();
        if (sections.isEmpty()) {
            LOGGER.error("使用手册未加载：{} 为空或缺失，AI 的『平台怎么用』类问题将无法回答（检查构建期资源拷贝）", RESOURCE);
        } else {
            LOGGER.info("使用手册已加载：{} 字，{} 章节 / {} 小节", markdown.length(),
                    sections.stream().map(ManualSection::chapterTitle).distinct().count(), sections.size());
        }
    }

    /** 手册全文（Markdown 原文；未加载时为提示语） */
    public String markdown() {
        return sections.isEmpty()
                ? "# 使用手册未加载\n\n手册资源缺失（`/ai/05-使用手册.md`），请重新构建应用。"
                : markdown;
    }

    /** 章节清单（每行一章，供工具尾部提示与页面侧栏使用） */
    public String chapterToc() {
        return chapterToc;
    }

    /**
     * 按 topic（章节/小节名，可含口语）或 question（自由提问）解析出最匹配的一节。
     *
     * @return 命中的小节；两个入参都没命中返回 null（调用方回目录，不要猜）
     */
    public ManualSection resolve(String topic, String question) {
        String query = normalize(nullToEmpty(topic) + " " + nullToEmpty(question));
        Set<String> grams = bigrams(query);
        if (grams.isEmpty()) {
            return null;
        }
        ManualSection best = null;
        int bestScore = 0;
        for (ManualSection section : sections) {
            int score = hits(grams, normalize(section.chapterTitle())) * CHAPTER_TITLE_WEIGHT
                    + hits(grams, normalize(section.title())) * SECTION_TITLE_WEIGHT
                    + hits(grams, normalize(section.body()));
            if (score > bestScore) {
                bestScore = score;
                best = section;
            }
        }
        return best;
    }

    /**
     * 渲染成给模型看的文本：命中节正文（超长按行切段）+ 本章其它小节 + 手册章节清单。
     *
     * @param topic    章节/小节名（可空）
     * @param question 用户原问题（可空）
     * @param part     第几段（从 1 起；null/越界按 1 处理）
     */
    public String render(String topic, String question, Integer part) {
        if (sections.isEmpty()) {
            return "使用手册未加载（资源缺失），无法回答平台使用类问题。请如实告知用户并建议其查看 docs/05-使用手册.md。";
        }
        ManualSection hit = resolve(topic, question);
        if (hit == null) {
            return "手册里没有匹配到具体章节。手册包含以下章节：\n" + chapterToc
                    + "\n请用更具体的说法再查一次（例如「回测参数」「怎么配置模型」「导入为什么覆盖」「额度超了怎么办」）。";
        }
        List<String> parts = splitByLines(hit.body(), MAX_CHARS);
        int index = part == null || part < 1 || part > parts.size() ? 1 : part;
        StringBuilder text = new StringBuilder(2048);
        text.append("【").append(hit.chapterTitle()).append(" › ").append(hit.title()).append("】\n")
                .append(parts.get(index - 1).strip());
        if (parts.size() > 1) {
            text.append("\n（本节共 ").append(parts.size()).append(" 段，这是第 ").append(index).append(" 段");
            if (index < parts.size()) {
                text.append("；需要后续内容时再用同一 topic 调一次，part=").append(index + 1);
            }
            text.append("）");
        }
        List<String> siblings = sectionsOfChapter(hit.chapterTitle());
        if (siblings.size() > 1) {
            text.append("\n本章还有：").append(String.join("、", siblings));
        }
        text.append("\n本手册章节：").append(chapterToc);
        return text.toString();
    }

    /** 某章节下的全部小节标题（用于"本章还有…"提示） */
    public List<String> sectionsOfChapter(String chapterTitle) {
        return sections.stream()
                .filter(section -> section.chapterTitle().equals(chapterTitle))
                .map(ManualSection::title)
                .filter(title -> !title.isBlank())
                .toList();
    }

    /** 读取随包发布的手册（失败只告警并降级，不让应用起不来） */
    private String load() {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("读取使用手册失败：{}（{}）", RESOURCE, e.getMessage());
            return "";
        } catch (UncheckedIOException e) {
            LOGGER.error("读取使用手册失败：{}（{}）", RESOURCE, e.getMessage());
            return "";
        }
    }

    /** 解析 Markdown：`## 章节` / `### 小节`，小节正文归到最近的小节 */
    private List<ManualSection> parse(String text) {
        List<ManualSection> list = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return list;
        }
        String chapter = "";
        String title = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.startsWith("## ")) {
                appendSection(list, chapter, title, body);
                chapter = line.substring(3).strip();
                title = null;
                body.setLength(0);
            } else if (line.startsWith("### ")) {
                appendSection(list, chapter, title, body);
                title = line.substring(4).strip();
                body.setLength(0);
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        appendSection(list, chapter, title, body);
        return list;
    }

    /** 收集一节（标题为空或正文为空时跳过：空节没有检索价值） */
    private void appendSection(List<ManualSection> list, String chapter, String title, StringBuilder body) {
        if (title == null || title.isBlank() || body.isEmpty()) {
            return;
        }
        list.add(new ManualSection(chapter, title, body.toString()));
    }

    /** 章节清单（每行一章；供工具提示"还能问什么"） */
    private String buildChapterToc() {
        return sections.stream()
                .map(ManualSection::chapterTitle)
                .filter(chapter -> !chapter.isBlank())
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("（无）");
    }

    /**
     * 按行切段（不切断表格行）：每段字符数不超过 max。
     *
     * @param body 正文
     * @param max  单段上限
     */
    private List<String> splitByLines(String body, int max) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : body.split("\n")) {
            if (current.length() > 0 && current.length() + line.length() + 1 > max) {
                parts.add(current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts.isEmpty() ? List.of("") : parts;
    }

    /** 命中词数（去重后计数：同一节里重复出现同一个词不重复加分） */
    private int hits(Set<String> grams, String text) {
        if (text.isEmpty()) {
            return 0;
        }
        Set<String> found = new LinkedHashSet<>();
        for (String gram : grams) {
            if (text.contains(gram)) {
                found.add(gram);
            }
        }
        return found.size();
    }

    /** 查询归一化：口语同义替换 + 去掉标点空白 + 转小写（Token/token 统一） */
    private String normalize(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result.replaceAll("[\\p{Punct}\\s，。、；：？！（）《》【】…—·“”‘’]+", "").toLowerCase();
    }

    /** 邻接二元组（中文按字切分的最小可用检索键） */
    private Set<String> bigrams(String text) {
        Set<String> grams = new LinkedHashSet<>();
        if (text.length() == 1) {
            grams.add(text);
            return grams;
        }
        for (int i = 0; i + 2 <= text.length(); i++) {
            grams.add(text.substring(i, i + 2));
        }
        return grams;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 手册是否可用（供接口判断，避免页面拿到空内容还不明所以） */
    public void requireLoaded() {
        if (sections.isEmpty()) {
            throw new BizException("使用手册未加载：请重新构建应用（手册资源随 jar 发布）");
        }
    }
}
