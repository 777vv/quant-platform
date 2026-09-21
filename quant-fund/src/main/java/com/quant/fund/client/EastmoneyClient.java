package com.quant.fund.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import com.quant.common.util.StrUtils;

import tools.jackson.databind.JsonNode;

/**
 * 东方财富/中证指数 公开接口封装（技术文档 5.1/5.4，接口契约均经 curl 实测确认）：
 * 串行限频 + 超时 + 指数退避重试 + 总开关熔断；全部解析为不可变记录。
 */
@Component
public class EastmoneyClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EastmoneyClient.class);

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private static final String URL_FUND_SUGGEST =
            "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key={code}";

    /** fundmobapi 被 WAF 按 TLS 指纹拦截（M2 实测 61136403），档案改用 f10 HTML 页面补齐 */
    private static final String URL_F10_HTML = "https://fundf10.eastmoney.com/jbgk_{code}.html";

    /** 分红送配页（基金历史分红：权益登记日/除息日/每 10 份派现金/发放日） */
    private static final String URL_F10_DIVIDEND = "https://fundf10.eastmoney.com/fhsp_{code}.html";

    /** 分红送配表格行：行内含"每10份派现金X元"，日期按出现顺序为 权益登记日、除息日、发放日 */
    private static final java.util.regex.Pattern PATTERN_DIVIDEND_ROW =
            java.util.regex.Pattern.compile("<tr>[\\s\\S]{0,600}?每10份派现金\\s*([0-9.]+)\\s*元[\\s\\S]{0,600}?</tr>");

    private static final java.util.regex.Pattern PATTERN_DATE =
            java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /** f10 页面两种成立日期形态：顶部摘要"成立日期：<span>yyyy-MM-dd</span>" 与表格"成立日期/规模</th><td>yyyy年MM月dd日" */
    private static final java.util.regex.Pattern PATTERN_ESTAB_SPAN =
            java.util.regex.Pattern.compile("成立日期[：:]\\s*<span>(\\d{4})-(\\d{2})-(\\d{2})");

    private static final java.util.regex.Pattern PATTERN_ESTAB_TABLE =
            java.util.regex.Pattern.compile("成立日期/规模</th><td>\\s*(\\d{4})年(\\d{1,2})月(\\d{1,2})日");

    /** f10 管理费率/托管费率/销售服务费率："费率</th><td>0.20%（每年）"；"---" 不适用则不命中 */
    private static final java.util.regex.Pattern PATTERN_MGMT_FEE =
            java.util.regex.Pattern.compile("管理费率</th><td>\\s*([0-9.]+)%");

    private static final java.util.regex.Pattern PATTERN_CUST_FEE =
            java.util.regex.Pattern.compile("托管费率</th><td>\\s*([0-9.]+)%");

    private static final java.util.regex.Pattern PATTERN_SALES_FEE =
            java.util.regex.Pattern.compile("销售服务费率</th><td>\\s*([0-9.]+)%");

    /**
     * f10 净资产规模（亿元）。实际 HTML 为 {@code 净资产规模：<span>\n115.45亿元\r\n（截止至：2026-06-30）}，
     * 标签与冒号夹在标签名与数字之间，故用"非数字字符"跳过而不是 {@code \s*}。
     */
    private static final java.util.regex.Pattern PATTERN_SCALE =
            java.util.regex.Pattern.compile("净资产规模[^0-9]{0,20}?([0-9.,]+)\\s*亿元");

    /** 规模截止日：日期有"yyyy-MM-dd"与"yyyy年MM月dd日"两种形态，斜杠与年月日都要兼容 */
    private static final java.util.regex.Pattern PATTERN_SCALE_DATE =
            java.util.regex.Pattern.compile("净资产规模[\\s\\S]{0,80}?（截止至：(\\d{4})[-年](\\d{1,2})[-月](\\d{1,2})");

    private static final java.util.regex.Pattern PATTERN_INDEX_NAME =
            java.util.regex.Pattern.compile("跟踪标的</th><td>([^<]+)");

    /** 常见指数名称 → 指数代码（估值数据源为中证官网，仅覆盖中证/上证系列；深市系列无估值来源不映射） */
    private static final Map<String, String> INDEX_CODE_BY_NAME = Map.ofEntries(
            Map.entry("沪深300指数", "000300"),
            Map.entry("沪深300", "000300"),
            Map.entry("上证50指数", "000016"),
            Map.entry("上证50", "000016"),
            Map.entry("中证500指数", "000905"),
            Map.entry("中证500", "000905"),
            Map.entry("中证1000指数", "000852"),
            Map.entry("中证1000", "000852"),
            Map.entry("中证800指数", "000906"),
            Map.entry("中证100指数", "000903"),
            Map.entry("中证红利指数", "000922"),
            Map.entry("中证红利", "000922"),
            Map.entry("上证指数", "000001"),
            Map.entry("科创50", "000688"),
            Map.entry("上证科创板50成份指数", "000688"),
            Map.entry("红利指数", "000015"));

    private static final String URL_ETF_REALTIME =
            "https://push2.eastmoney.com/api/qt/stock/get?secid={secid}"
                    + "&fields=f57,f58,f43,f60,f170,f107&fltt=2";

    private static final String URL_ETF_KLINE_PATH =
            "/api/qt/stock/kline/get?secid={secid}"
                    + "&klt=101&fqt={fqt}&beg={beg}&end={end}"
                    + "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57";

    /**
     * K 线接口域名列表（主域 + 东财官方编号分片域名，网页端即按 {N}.push2his 分片调用）。
     * 主域在链路抖动窗口会出现 TLS 断连（实测 "header parser received no bytes"），
     * 主域重试耗尽后依次切换备用域名，全部失败才判定为不可用。
     */
    private static final String[] KLINE_HOSTS = {
            "https://push2his.eastmoney.com",
            "https://1.push2his.eastmoney.com",
            "https://24.push2his.eastmoney.com",
            "https://48.push2his.eastmoney.com"
    };

    private static final String URL_OTC_NAV =
            "https://api.fund.eastmoney.com/f10/lsjz?fundCode={code}&pageIndex={pageIndex}"
                    + "&pageSize={pageSize}&startDate={startDate}&endDate={endDate}";

    private static final String REFERER_F10 = "http://fundf10.eastmoney.com/jbgk_{code}.html";

    private static final String URL_INDEX_QUOTES =
            "https://push2.eastmoney.com/api/qt/ulist.np/get?secids={secids}"
                    + "&fields=f2,f3,f4,f12,f13,f14,f124&fltt=2";

    private static final String URL_INDEX_VALUATION =
            "https://www.csindex.com.cn/csindex-home/perf/index-perf?indexCode={indexCode}"
                    + "&startDate={startDate}&endDate={endDate}";

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EastmoneyProperties properties;

    private final RestClient restClient;

    /** 全局串行限频：上次请求时间戳 */
    private long lastRequestAt = 0L;

    /**
     * 基金档案（ETF 与场外通用）：fundsuggest 判存在性与类型 + f10 页面补成立日期/跟踪标的/规模/费率。
     *
     * @param code          基金代码
     * @param name          基金名称
     * @param fundType      东财基金类型（"ETF" 等）
     * @param estabDate     成立日期
     * @param indexCode     跟踪指数代码（按名称映射，未命中为 null）
     * @param indexName     跟踪指数名称
     * @param company       基金公司
     * @param fundScale     净资产规模（亿元），f10 披露口径
     * @param fundScaleDate 规模截止日
     * @param mgmtFeeRate   管理费率（%/年）
     * @param custFeeRate   托管费率（%/年）
     * @param salesFeeRate  销售服务费率（%/年），"---" 表示不适用
     */
    public record FundProfile(String code, String name, String fundType, LocalDate estabDate,
                              String indexCode, String indexName, String company,
                              BigDecimal fundScale, LocalDate fundScaleDate,
                              BigDecimal mgmtFeeRate, BigDecimal custFeeRate, BigDecimal salesFeeRate) {
    }

    /** ETF 实时行情 */
    public record EtfRealtime(String code, String name, BigDecimal price, BigDecimal prevClose,
                              BigDecimal changePct) {
    }

    /** ETF 日K（前复权），klines 字段顺序：日期,开,收,高,低,量,额 */
    public record KlineItem(LocalDate date, BigDecimal open, BigDecimal close, BigDecimal high,
                            BigDecimal low, long volume, BigDecimal amount) {
    }

    /** 场外净值页 */
    public record NavItem(LocalDate date, BigDecimal unitNav, BigDecimal accNav, BigDecimal dailyGrowth) {
    }

    /** 场外净值分页结果 */
    public record NavPage(int pageIndex, int pageSize, long totalCount, List<NavItem> items) {

        public boolean hasNext() {
            return (long) pageIndex * pageSize < totalCount;
        }
    }

    /** 全球指数行情 */
    public record IndexQuoteItem(String secid, String code, String name, BigDecimal price,
                                 BigDecimal changePct, BigDecimal changeAmt, long quoteTime) {
    }

    /** 指数估值（peg 即 PE，中证官网口径；PB 暂无来源留空） */
    public record ValuationItem(LocalDate date, BigDecimal pe) {
    }

    public EastmoneyClient(EastmoneyProperties properties) {
        this.properties = properties;
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", UA)
                .build();
    }

    public FundProfile fetchFundProfile(String code) {
        JsonNode datas = getJson(URL_FUND_SUGGEST.replace("{code}", code)).path("Datas");
        if (!datas.isArray() || datas.isEmpty()) {
            throw new BizException("基金不存在: " + code);
        }
        JsonNode first = datas.get(0);
        String name = first.path("NAME").asText("");
        String fundType = first.path("FundBaseInfo").path("FTYPE").asText("");
        String company = first.path("FundBaseInfo").path("JJGS").asText("");
        // f10 页面补齐成立日期与跟踪标的（失败不阻断：字段可空）
        LocalDate estabDate = null;
        String indexName = null;
        String indexCode = null;
        BigDecimal fundScale = null;
        LocalDate fundScaleDate = null;
        BigDecimal mgmtFeeRate = null;
        BigDecimal custFeeRate = null;
        BigDecimal salesFeeRate = null;
        try {
            String html = getWithRetry(URL_F10_HTML.replace("{code}", code), null);
            java.util.regex.Matcher span = PATTERN_ESTAB_SPAN.matcher(html);
            java.util.regex.Matcher table = PATTERN_ESTAB_TABLE.matcher(html);
            if (span.find()) {
                estabDate = LocalDate.parse(span.group(1) + "-" + span.group(2) + "-" + span.group(3));
            } else if (table.find()) {
                estabDate = LocalDate.of(Integer.parseInt(table.group(1)),
                        Integer.parseInt(table.group(2)), Integer.parseInt(table.group(3)));
            }
            java.util.regex.Matcher index = PATTERN_INDEX_NAME.matcher(html);
            if (index.find()) {
                indexName = index.group(1).trim();
                indexCode = INDEX_CODE_BY_NAME.get(indexName);
            }
            fundScale = percentOrScale(html, PATTERN_SCALE);
            java.util.regex.Matcher scaleDate = PATTERN_SCALE_DATE.matcher(html);
            if (scaleDate.find()) {
                fundScaleDate = LocalDate.of(Integer.parseInt(scaleDate.group(1)),
                        Integer.parseInt(scaleDate.group(2)), Integer.parseInt(scaleDate.group(3)));
            }
            mgmtFeeRate = percentOrScale(html, PATTERN_MGMT_FEE);
            custFeeRate = percentOrScale(html, PATTERN_CUST_FEE);
            salesFeeRate = percentOrScale(html, PATTERN_SALES_FEE);
        } catch (Exception e) {
            LOGGER.warn("基金[{}]f10档案解析失败（不影响导入）: {}", code, e.getMessage());
        }
        return new FundProfile(code, name, fundType, estabDate, indexCode, indexName, company,
                fundScale, fundScaleDate, mgmtFeeRate, custFeeRate, salesFeeRate);
    }

    /**
     * 基金分红记录（东财分红送配页）。
     * 页面表格列序为「年份 | 权益登记日 | 除息日 | 每 10 份派现金 | 分红发放日」，
     * 解析时按行内日期出现顺序取前三个（登记日/除息日/发放日），除息日缺失的行直接跳过。
     *
     * @param code 基金代码
     * @return 按除息日升序的分红记录；解析失败返回空列表（不阻断同步）
     */
    public List<DividendItem> fetchFundDividends(String code) {
        List<DividendItem> items = new ArrayList<>();
        try {
            String html = getWithRetry(URL_F10_DIVIDEND.replace("{code}", code), REFERER_F10.replace("{code}", code));
            java.util.regex.Matcher row = PATTERN_DIVIDEND_ROW.matcher(html);
            while (row.find()) {
                java.util.regex.Matcher dates = PATTERN_DATE.matcher(row.group(0));
                List<LocalDate> found = new ArrayList<>();
                while (dates.find() && found.size() < 3) {
                    found.add(LocalDate.parse(dates.group(1)));
                }
                if (found.size() < 2) {
                    continue;
                }
                items.add(new DividendItem(found.get(0), found.get(1),
                        found.size() > 2 ? found.get(2) : null, decimal(row.group(1))));
            }
        } catch (Exception e) {
            LOGGER.warn("基金[{}]分红记录解析失败（不影响同步）: {}", code, e.getMessage());
        }
        items.sort(java.util.Comparator.comparing(DividendItem::exDate));
        return items;
    }

    /**
     * 分红记录。
     *
     * @param recordDate 权益登记日
     * @param exDate     除息日（图上标记取此日）
     * @param payDate    分红发放日
     * @param per10Amount 每 10 份派现金（元）
     */
    public record DividendItem(LocalDate recordDate, LocalDate exDate, LocalDate payDate, BigDecimal per10Amount) {
    }

    /** market: 1=沪 0=深；无行情（非场内/请求被拒）返回 empty。探测用途：单次尝试不重试 */
    public Optional<EtfRealtime> fetchEtfRealtime(int market, String code) {
        String url = URL_ETF_REALTIME.replace("{secid}", market + "." + code);
        String body;
        try {
            body = tryGetOnce(url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (body == null) {
            return Optional.empty();
        }
        JsonNode data = JsonUtils.mapper().readTree(body).path("data");
        if (data.isMissingNode() || data.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new EtfRealtime(
                data.path("f57").asText(),
                data.path("f58").asText(""),
                decimal(data.path("f43")),
                decimal(data.path("f60")),
                decimal(data.path("f170"))));
    }

    /** 单次限频请求：非 2xx/连接异常返回 null（用于场内探测，push2 对无效 secid 会直接断连） */
    private synchronized String tryGetOnce(String url) throws InterruptedException {
        long wait = lastRequestAt + properties.getIntervalMs() - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            lastRequestAt = System.currentTimeMillis();
            return body;
        } catch (Exception e) {
            lastRequestAt = System.currentTimeMillis();
            LOGGER.debug("探测请求无响应(视为非场内): {} - {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 拉取 ETF 日K（前复权 fqt=1，服务端按 [beg,end] 过滤），klines 顺序：日期,开,收,高,低,量,额。
     * 域名容灾：主域重试耗尽后依次尝试编号分片域名。实测拦截为"短惩罚窗口"（约 30-60 秒，疑似按请求突发限流），
     * 故每个备用域名之间停顿 15 秒，让惩罚窗口自然过期，而不是 15 秒内打满 12 连发再次触发限流。
     * 全部失败才抛出（K 线是导入/同步/迷你线/基准的共同依赖）。
     * ⚠️ 本方法最坏耗时可达 1 分钟级，**只用于用户可等待的主流程**（导入/同步/基准）；
     * 装饰性、批量循环场景（如 14 个指数的迷你线）必须用 {@link #fetchEtfKlineFast}。
     */
    public List<KlineItem> fetchEtfKline(int market, String code, LocalDate beg, LocalDate end) {
        return fetchKline(market, code, beg, end, true);
    }

    /**
     * 快速版日K拉取：仅试主域、不重试、不做域名退避——用于装饰性数据或批量循环
     * （典型场景：看板 14 个指数的迷你线构建），避免网络不通时把请求线程拖成长任务。
     */
    public List<KlineItem> fetchEtfKlineFast(int market, String code, LocalDate beg, LocalDate end) {
        return fetchKline(market, code, beg, end, false);
    }

    /**
     * 未复权日K拉取（fqt=0）：分红股息率等口径需要"真实价格"作分母——
     * 前复权价把历史价格压低（等于把分红从价格里抹掉），直接当分母会系统性高估历史股息率。
     * 走带退避的慢通道（调用方是每日全量同步任务，非请求路径），失败由调用方吞掉、留空降级。
     */
    public List<KlineItem> fetchEtfKlineUnadjusted(int market, String code, LocalDate beg, LocalDate end) {
        return fetchKline(market, code, beg, end, true, "0");
    }

    /** 日K拉取实现（默认 fqt=1 前复权）：allowFallback=false 时只打一次主域且不重试 */
    private List<KlineItem> fetchKline(int market, String code, LocalDate beg, LocalDate end, boolean allowFallback) {
        return fetchKline(market, code, beg, end, allowFallback, "1");
    }

    /**
     * 日K拉取实现。
     *
     * @param allowFallback true=主域重试并切换备用域名（带停顿）；false=只打一次主域
     * @param fqt           复权口径：1=前复权（默认） 0=不复权（真实价格）
     */
    private List<KlineItem> fetchKline(int market, String code, LocalDate beg, LocalDate end, boolean allowFallback,
                                       String fqt) {
        String path = URL_ETF_KLINE_PATH.replace("{secid}", market + "." + code)
                .replace("{fqt}", fqt)
                .replace("{beg}", COMPACT.format(beg))
                .replace("{end}", COMPACT.format(end));
        if (!allowFallback) {
            // 快速通道：主域 + 一个备用域（实测封堵常按域名/路径生效，换域有较大概率绕过），
            // 每次仅 1 次尝试、域名间短间隔，整体耗时可控（≤ 约 1 秒 + 限频间隔）。
            BizException lastFastError = null;
            for (int i = 0; i < Math.min(2, KLINE_HOSTS.length); i++) {
                String body;
                try {
                    body = tryGetOnce(KLINE_HOSTS[i] + path);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("快速拉取被中断");
                }
                if (body != null) {
                    JsonNode fast = com.quant.common.util.JsonUtils.mapper().readTree(body).path("data").path("klines");
                    return parseKlines(fast, null);
                }
                lastFastError = new BizException("快速拉取失败（" + KLINE_HOSTS[i] + "）");
                if (i == 0) {
                    sleep(FAST_PATH_HOST_GAP_MS);
                }
            }
            throw lastFastError;
        }
        JsonNode klines = null;
        BizException lastError = null;
        for (int i = 0; i < KLINE_HOSTS.length; i++) {
            String host = KLINE_HOSTS[i];
            try {
                klines = getJson(host + path).path("data").path("klines");
                break;
            } catch (BizException e) {
                lastError = e;
                boolean hasMore = i < KLINE_HOSTS.length - 1;
                LOGGER.warn("K线接口[{}]不可用({})，{}: {}", host,
                        isConnectionReset(e.getMessage()) ? "疑似限流拦截" : "请求失败",
                        hasMore ? "15秒后切换备用域名" : "无备用域名可用", e.getMessage());
                if (hasMore) {
                    sleep(FALLBACK_HOST_PAUSE_MS);
                }
            }
        }
        if (klines == null) {
            throw new BizException(friendlySourceError(lastError));
        }
        return parseKlines(klines, null);
    }

    /** klines 数组 → 记录列表；为空且给定错误文案时抛业务异常 */
    private List<KlineItem> parseKlines(JsonNode klines, String emptyError) {
        if (klines == null || klines.isMissingNode() || klines.isNull()) {
            if (emptyError != null) {
                throw new BizException(emptyError);
            }
            return List.of();
        }
        List<KlineItem> items = new ArrayList<>(klines.size());
        for (JsonNode node : klines) {
            String[] parts = node.asText().split(",");
            if (parts.length < 7) {
                continue;
            }
            items.add(new KlineItem(
                    LocalDate.parse(parts[0]),
                    new BigDecimal(parts[1]),
                    new BigDecimal(parts[2]),
                    new BigDecimal(parts[3]),
                    new BigDecimal(parts[4]),
                    Long.parseLong(parts[5]),
                    new BigDecimal(parts[6])));
        }
        return items;
    }

    /** 备用域名切换前的停顿毫秒：给限流惩罚窗口留出过期时间 */
    private static final long FALLBACK_HOST_PAUSE_MS = 15_000L;

    /** 快速通道换域前的短间隔（毫秒）：仅用于绕开按域名的封堵，不做长退避 */
    private static final long FAST_PATH_HOST_GAP_MS = 500L;

    /** 连接被重置类错误特征（实测为数据源按突发请求限流/拦截，惩罚窗口约 30-60 秒） */
    private boolean isConnectionReset(String message) {
        return message != null && (message.contains("header parser received no bytes")
                || message.contains("Connection reset") || message.contains("connection was reset"));
    }

    /** 面向用户的报错：连接重置类给出可操作指引，其余原样透出 */
    private String friendlySourceError(BizException e) {
        if (isConnectionReset(e.getMessage())) {
            return "数据源暂时拒绝连接（疑似限流，通常 1 分钟内自行恢复），请稍候重试；"
                    + "期间仪表盘/行情仍显示库内已有数据";
        }
        return e.getMessage();
    }

    /** 拉取一页场外历史净值（需 Referer；分页字段在响应顶层，pageSize 被服务端钳为 20） */
    public NavPage fetchOtcNavPage(String code, int pageIndex, int pageSize, LocalDate beg, LocalDate end) {
        String url = URL_OTC_NAV.replace("{code}", code)
                .replace("{pageIndex}", String.valueOf(pageIndex))
                .replace("{pageSize}", String.valueOf(pageSize))
                .replace("{startDate}", beg == null ? "" : COMPACT.format(beg))
                .replace("{endDate}", end == null ? "" : COMPACT.format(end));
        JsonNode root = getJson(url, REFERER_F10.replace("{code}", code));
        JsonNode list = root.path("Data").path("LSJZList");
        List<NavItem> items = new ArrayList<>();
        if (list.isArray()) {
            for (JsonNode node : list) {
                String date = node.path("FSRQ").asText("");
                if (date.isBlank()) {
                    continue;
                }
                items.add(new NavItem(
                        LocalDate.parse(date),
                        decimal(node.path("DWJZ")),
                        decimal(node.path("LJJZ")),
                        decimal(node.path("JZZZL"))));
            }
        }
        // 分页字段在响应顶层（Data 内没有）；服务端会将 pageSize 钳制为 20，以返回值为准
        long total = root.path("TotalCount").asLong(0);
        int actualPageSize = root.path("PageSize").asInt(items.size());
        return new NavPage(pageIndex, actualPageSize, total, items);
    }

    /** 批量拉取全球指数实时行情（secid 列表一次请求，fltt=2 直接返回小数） */
    public List<IndexQuoteItem> fetchIndexQuotes(List<String> secids) {
        String url = URL_INDEX_QUOTES.replace("{secids}", String.join(",", secids));
        JsonNode diff = getJson(url).path("data").path("diff");
        List<IndexQuoteItem> items = new ArrayList<>();
        if (diff.isArray()) {
            for (JsonNode node : diff) {
                items.add(new IndexQuoteItem(
                        node.path("f13").asInt() + "." + node.path("f12").asText(),
                        node.path("f12").asText(),
                        node.path("f14").asText(""),
                        decimal(node.path("f2")),
                        decimal(node.path("f3")),
                        decimal(node.path("f4")),
                        node.path("f124").asLong()));
            }
        }
        return items;
    }

    /** 拉取指数估值历史（中证官网，peg 即 PE；仅覆盖中证/上证系列） */
    public List<ValuationItem> fetchIndexValuation(String indexCode, LocalDate beg, LocalDate end) {
        String url = URL_INDEX_VALUATION.replace("{indexCode}", indexCode)
                .replace("{startDate}", COMPACT.format(beg))
                .replace("{endDate}", COMPACT.format(end));
        JsonNode data = getJson(url).path("data");
        List<ValuationItem> items = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                String date = node.path("tradeDate").asText("");
                if (date.isBlank()) {
                    continue;
                }
                items.add(new ValuationItem(
                        LocalDate.parse(date, COMPACT),
                        decimal(node.path("peg"))));
            }
        }
        return items;
    }

    /**
     * 从 f10 HTML 里按正则取数值（规模/费率共用）。
     * 千分位逗号先剔除；未命中（如费率显示 "---"）返回 null。
     *
     * @param html    f10 页面 HTML
     * @param pattern 已编译的匹配模式，第 1 组为数值
     */
    private BigDecimal percentOrScale(String html, java.util.regex.Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1).replace(",", "").trim();
        return raw.isEmpty() ? null : new BigDecimal(raw);
    }

    /** 字符串转 BigDecimal：空/占位符"-"返回 null */
    private BigDecimal decimal(String text) {
        if (StrUtils.isBlank(text) || "-".equals(text.trim())) {
            return null;
        }
        return new BigDecimal(text.trim());
    }

    /** 节点转 BigDecimal：缺失/null/占位符"-"返回 null（停牌等场景） */
    private BigDecimal decimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (StrUtils.isBlank(text) || "-".equals(text)) {
            return null;
        }
        return new BigDecimal(text);
    }

    /** GET 并解析 JSON（无 Referer） */
    private JsonNode getJson(String url) {
        return getJson(url, null);
    }

    /** GET 并解析 JSON（可带 Referer） */
    private JsonNode getJson(String url, String referer) {
        String body = getWithRetry(url, referer);
        return com.quant.common.util.JsonUtils.mapper().readTree(body);
    }

    /** 带重试的 GET：指数退避 1s/2s/4s，重试耗尽抛业务异常；总开关关闭时直接熔断 */
    private String getWithRetry(String url, String referer) {
        if (!properties.isEnabled()) {
            throw new BizException("数据源已熔断（quant.eastmoney.enabled=false），请稍后再试");
        }
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= properties.getRetryTimes(); attempt++) {
            try {
                return throttledGet(url, referer);
            } catch (RestClientException e) {
                lastError = e;
                long backoffMs = 1000L * (1L << (attempt - 1));
                LOGGER.warn("数据源请求失败(第{}次): {} - {}，{}ms后重试", attempt, url, e.getMessage(), backoffMs);
                sleep(backoffMs);
            }
        }
        throw new BizException("数据源请求失败（已重试" + properties.getRetryTimes() + "次）: " + lastError.getMessage());
    }

    /** 串行限频 GET：保证相邻请求间隔 ≥ interval-ms（全局锁） */
    private synchronized String throttledGet(String url, String referer) {
        long wait = lastRequestAt + properties.getIntervalMs() - System.currentTimeMillis();
        if (wait > 0) {
            sleep(wait);
        }
        long start = System.currentTimeMillis();
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url);
        if (StrUtils.isNotBlank(referer)) {
            spec = spec.header("Referer", referer);
        }
        String body = spec.retrieve().body(String.class);
        lastRequestAt = System.currentTimeMillis();
        LOGGER.debug("GET {} -> {}ms, {}字节", url, lastRequestAt - start, body == null ? 0 : body.length());
        return body;
    }

    /** 可中断休眠 */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("数据源请求被中断");
        }
    }
}
