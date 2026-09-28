package com.quant.strategy.notify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.entity.SysWecomConfig;
import com.quant.strategy.mapper.SysWecomConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.quant.strategy.core.Signal;

import tools.jackson.databind.JsonNode;

/**
 * 微信通知服务实现（V5.20，方案①：企业微信自建应用 + 微信插件）。
 *
 * <p>消息链路：本平台 → 企业微信 API（自建应用消息）→ 用户在个人微信「企业微信通知」
 * 或企业微信 App 中接收。两条通道（邮件/微信）完全并行、互不影响：
 * <b>微信发送失败只记日志</b>，绝不拖累邮件与信号计算。
 *
 * <p>企业微信接口：
 * <ul>
 *   <li>取 access_token：GET /cgi-bin/gettoken?corpid=…&corpsecret=…（有效期 7200s，本服务缓存）；</li>
 *   <li>发应用消息：POST /cgi-bin/message/send?access_token=…（msgtype=text，touser=@all 或指定成员）。</li>
 * </ul>
 */
@Service
public class WeComNotifyServiceImpl implements WeComNotifyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeComNotifyServiceImpl.class);

    /** 消息正文上限（企业微信 text 类型上限 2048 字节，中文 3 字节/字符，取 600 字符留足余量） */
    private static final int CONTENT_MAX_CHARS = 600;

    /** 日期格式化（信号摘要标题用） */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SysWecomConfigMapper configMapper;

    private final RestClient restClient = RestClient.create();

    /** 缓存的 access_token 与过期时间戳（毫秒），多实例下各自缓存亦可接受 */
    private final AtomicReference<String[]> tokenCache = new AtomicReference<>();

    public WeComNotifyServiceImpl(SysWecomConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    /** 单行配置（表由 schema 初始化，缺行时返回带默认值的空壳，不抛错） */
    private SysWecomConfig loadConfig() {
        SysWecomConfig row = configMapper.selectById(1L);
        if (row == null) {
            row = new SysWecomConfig();
            row.setId(1L);
            row.setEnabled(0);
            row.setTouser("@all");
        }
        return row;
    }

    @Override
    public WeComConfigVO config() {
        return WeComConfigVO.of(loadConfig());
    }

    @Override
    public void saveConfig(WeComConfigRequest request) {
        if (request == null) {
            throw new BizException("配置内容不能为空");
        }
        SysWecomConfig current = loadConfig();
        // ⚠️ 打码回填规则（与邮件授权码、AI Token 同一约定，AGENTS.md 铁律 8）：
        // 企业在界面回显的是打码值（如 ww6f***），用户不动它直接保存时**绝不能把打码值写回库**——
        // "留空或含 *" 一律沿用已存值。
        String secret = keepIfMasked(request.secret(), current.getSecret());
        String corpid = keepIfMasked(request.corpid(), current.getCorpid());
        if (Boolean.TRUE.equals(request.enabled())) {
            // 启用时四项配置必须齐全，避免"开着开关却发不出去"
            if (isBlank(corpid)) {
                throw new BizException("启用微信通知前请填写企业 ID");
            }
            if (isBlank(request.agentId()) && isBlank(current.getAgentId())) {
                throw new BizException("启用微信通知前请填写 AgentId");
            }
            if (isBlank(secret)) {
                throw new BizException("启用微信通知前请填写 Secret");
            }
        }
        SysWecomConfig row = new SysWecomConfig();
        row.setId(1L);
        row.setEnabled(request.enabled() == null ? current.getEnabled() : (request.enabled() ? 1 : 0));
        row.setCorpid(corpid);
        row.setAgentId(keepIfMasked(request.agentId(), current.getAgentId()));
        row.setSecret(secret);
        row.setTouser(isBlank(request.touser()) ? "@all" : request.touser().trim());
        if (configMapper.selectById(1L) == null) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        // 配置变更后旧 access_token 立即失效
        tokenCache.set(null);
        LOGGER.info("微信通知配置已保存：enabled={} corpid={} agentId={}",
                row.getEnabled(), maskTail(row.getCorpid(), 4), row.getAgentId());
    }

    @Override
    public void testSend(WeComConfigRequest request) {
        // 测试发送**不要求开启开关**（与邮件卡同一口径）：关闭状态下也应能先验证凭据是否填对，
        // 否则用户必须先开开关才能测试，容易"开着开关却发不出去"
        SysWecomConfig stored = loadConfig();
        // 按"界面当前填写"测试（V5.26 用户口径：表单改了没保存，测的也应是眼前这套）；
        // 打码回显（ww***）与留空回退已存值，全程不落库
        SysWecomConfig row = new SysWecomConfig();
        row.setId(stored.getId());
        row.setEnabled(stored.getEnabled());
        row.setCorpid(keepIfMasked(request == null ? null : request.corpid(), stored.getCorpid()));
        row.setAgentId(keepIfMasked(request == null ? null : request.agentId(), stored.getAgentId()));
        row.setSecret(keepIfMasked(request == null ? null : request.secret(), stored.getSecret()));
        row.setTouser(firstText(request == null ? null : request.touser(),
                stored.getTouser() == null || stored.getTouser().isBlank() ? "@all" : stored.getTouser()));
        if (!isConfigured(row)) {
            throw new BizException("微信通知配置不完整：请先填写企业 ID、AgentId 与 Secret 再测试");
        }
        String content = "【个人量化投资助手】微信通知测试成功（" + DATE_FMT.format(LocalDate.now()) + "）。"
                + "后续交易信号将推送到此会话。";
        sendText(row, content);
        LOGGER.info("微信测试消息发送成功：touser={}（按界面当前填写测试，未改变已存配置）", row.getTouser());
    }

    @Override
    public void sendSignalDigest(List<SignalRecord> signals) {
        if (signals == null || signals.isEmpty()) {
            return;
        }
        SysWecomConfig row = loadConfig();
        if (!Integer.valueOf(1).equals(row.getEnabled()) || !isConfigured(row)) {
            return; // 未启用/未配置：静默跳过（微信通道可选，不影响邮件）
        }
        try {
            List<SignalRecord> actionable = signals.stream()
                    .filter(s -> Signal.BUY.equals(s.getDirection()) || Signal.SELL.equals(s.getDirection()))
                    .toList();
            if (actionable.isEmpty()) {
                return; // 仅 HOLD：不推送（与邮件口径一致）
            }
            String content = buildDigestText(actionable);
            sendText(row, content);
            LOGGER.info("微信信号推送成功：{} 条买卖信号", actionable.size());
        } catch (Exception e) {
            // 微信通道失败只记日志：绝不影响邮件通道与信号计算
            LOGGER.error("微信信号推送失败（不影响邮件）", e);
        }
    }

    /** 组装当日买卖信号文本（一条消息容纳全部，超长截断） */
    private String buildDigestText(List<SignalRecord> actionable) {
        LocalDate date = actionable.get(0).getSignalDate();
        StringBuilder sb = new StringBuilder("【个人量化投资助手】交易信号 ")
                .append(DATE_FMT.format(date)).append('\n');
        for (SignalRecord signal : actionable) {
            sb.append('\n')
                    .append(Signal.BUY.equals(signal.getDirection()) ? "▲ 买入 " : "▼ 卖出 ")
                    .append(signal.getFundCode());
            if (signal.getPriceAt() != null) {
                sb.append(" @").append(strip(signal.getPriceAt()));
            }
            if (signal.getSuggestDesc() != null && !signal.getSuggestDesc().isBlank()) {
                sb.append('\n').append(signal.getSuggestDesc());
            }
            sb.append('\n');
        }
        sb.append("\n详见平台【信号查询】，不构成投资建议。");
        String text = sb.toString();
        return text.length() <= CONTENT_MAX_CHARS ? text : text.substring(0, CONTENT_MAX_CHARS);
    }

    /** 测试与推送共用的发送路径：取配置 → 校验齐全 → 取 token → 发 text 消息 */
    private void sendText(SysWecomConfig row, String content) {
        String token = accessToken(row);
        String touser = isBlank(row.getTouser()) ? "@all" : row.getTouser().trim();
        String body = JsonUtils.toJson(java.util.Map.of(
                "touser", touser,
                "msgtype", "text",
                "agentid", parseAgentId(row.getAgentId()),
                "text", java.util.Map.of("content", content)));
        String resp = postJson("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token, body);
        JsonNode node = JsonUtils.mapper().readTree(resp);
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            throw new BizException("企业微信消息发送失败：" + hintOf(errcode, node.path("errmsg").asText("未知")));
        }
    }

    /** 取企业微信 access_token（带缓存：提前 200 秒过期重取） */
    private String accessToken(SysWecomConfig row) {
        String[] cached = tokenCache.get();
        if (cached != null && System.currentTimeMillis() < Long.parseLong(cached[1])) {
            return cached[0];
        }
        synchronized (tokenCache) {
            cached = tokenCache.get();
            if (cached != null && System.currentTimeMillis() < Long.parseLong(cached[1])) {
                return cached[0];
            }
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid="
                    + urlEncode(row.getCorpid()) + "&corpsecret=" + urlEncode(row.getSecret());
            String resp;
            try {
                resp = restClient.get().uri(url).retrieve().body(String.class);
            } catch (RestClientException e) {
                throw new BizException("企业微信接口连接失败：请检查网络后重试");
            }
            JsonNode node;
            try {
                node = JsonUtils.mapper().readTree(resp);
            } catch (Exception e) {
                throw new BizException("企业微信接口响应解析失败");
            }
            int errcode = node.path("errcode").asInt(-1);
            if (errcode != 0) {
                throw new BizException("企业微信接口错误：" + hintOf(errcode, node.path("errmsg").asText("未知")));
            }
            String token = node.path("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(7200);
            tokenCache.set(new String[]{token, String.valueOf(System.currentTimeMillis()
                    + Math.max(60, expiresIn - 200) * 1000L)});
            return token;
        }
    }

    /**
     * 企业微信 errcode → 可操作的中文提示（官方文档 https://developer.work.weixin.qq.com/document/path/90313）。
     * 目标是让用户看到报错就知道该改哪个字段，而不是去查官方文档。
     */
    private String hintOf(int errcode, String errmsg) {
        String hint = switch (errcode) {
            case 40001 -> "Secret 不正确或不属于该企业：请到「企业微信后台 → 应用管理 → 自建应用」详情页重新复制 Secret";
            case 40013 -> "企业 ID 不正确：请核对「我的企业 → 企业信息」里的企业 ID";
            case 40056 -> "AgentId 不正确：请核对自建应用详情页的 AgentId（纯数字）";
            case 60011 -> "该应用没有可用成员：请在自建应用「可见范围」里添加你自己";
            case 45009 -> "接口调用超过每日上限，请稍后再试";
            case 41001 -> "缺少 access_token 参数（内部错误，请重试）";
            case 42001 -> "access_token 已过期（内部错误，请重试）";
            default -> "请到企业微信后台核对企业 ID / AgentId / Secret";
        };
        return hint + "（errcode=" + errcode + "，errmsg=" + errmsg + "）";
    }

    /** 发送 JSON POST（企业微信接口），网络异常转业务异常 */
    private String postJson(String url, String json) {
        try {
            return restClient.post().uri(url)
                    .header("Content-Type", "application/json")
                    .body(json)
                    .retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new BizException("企业微信接口连接失败：请检查网络后重试");
        }
    }

    /** 四项关键配置是否齐全（供测试与推送前置判断） */
    private boolean isConfigured(SysWecomConfig row) {
        return !isBlank(row.getCorpid()) && !isBlank(row.getAgentId()) && !isBlank(row.getSecret());
    }

    /** 去掉无意义的小数零，便于文案阅读 */
    private String strip(BigDecimal value) {
        return value == null ? "无" : value.stripTrailingZeros().toPlainString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 返回第一个非空白的值（全空返回 null）；测试发送合并"界面填写 > 库内已存"时用 */
    private String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    /**
     * 打码回填保护：留空或含 * （界面回显的打码值）时沿用已存值，否则存新值。
     * 与邮件授权码、邮件发件账号、AI 模型 Token 同一约定（AGENTS.md 铁律 8）。
     */
    private String keepIfMasked(String incoming, String current) {
        return isBlank(incoming) || incoming.contains("*") ? current : incoming.trim();
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private int parseAgentId(String agentId) {
        try {
            return Integer.parseInt(agentId.trim());
        } catch (Exception e) {
            throw new BizException("AgentId 须为纯数字（企业微信后台-应用详情页可见）");
        }
    }

    private String maskTail(String value, int keep) {
        if (value == null || value.length() <= keep) {
            return value;
        }
        return value.substring(0, keep) + "***";
    }
}
