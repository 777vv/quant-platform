package com.quant.system.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quant.common.log.TraceIdFilter;
import com.quant.common.result.PageResult;
import com.quant.common.util.ClientIpUtils;
import com.quant.common.util.JsonUtils;
import com.quant.system.dto.LoginLogVO;
import com.quant.system.entity.LoginLog;
import com.quant.system.mapper.LoginLogMapper;
import com.quant.system.service.LoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录日志实现（V5.46/V5.47）。
 *
 * <p>归属地口径：内网/本机地址直接标注「内网」（本地开发与内网访问最常见的场景，无需解析）；
 * 公网地址先落「待解析」，随后在异步线程调用**在线 IP 库**解析并回填——
 * 依次尝试百度开放数据 → ip-api.com 两个免费源，单源 3 秒超时、全部失败保留「待解析」；
 * 解析开关 {@code login-log.ip-location-enabled}（默认 true，见 application.yml）。
 * **解析绝不阻塞登录主流程**：日志行先落库，解析在 taskExecutor 线程池异步执行。
 */
@Service
public class LoginLogServiceImpl implements LoginLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginLogServiceImpl.class);

    /** UA 入库截断长度（与 login_log.user_agent 列宽一致） */
    private static final int UA_MAX_LENGTH = 500;

    /** 归属地占位文案：公网 IP 解析失败/未开启时的展示值 */
    private static final String LOCATION_PENDING = "待解析";

    /** 用户名入库截断长度（与 login_log.username 列宽一致） */
    private static final int USERNAME_MAX_LENGTH = 64;

    /** JDK 自带 HTTP 客户端（零 Maven 依赖）；连接与响应均 3 秒超时，第三方挂死不拖垮线程池 */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final LoginLogMapper loginLogMapper;

    private final ThreadPoolTaskExecutor taskExecutor;

    /** 公网归属地在线解析开关（第三方接口，失败自动降级为「待解析」） */
    @Value("${login-log.ip-location-enabled:true}")
    private boolean ipLocationEnabled;

    public LoginLogServiceImpl(LoginLogMapper loginLogMapper,
                               @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.loginLogMapper = loginLogMapper;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void record(String username, boolean success, String failReason) {
        try {
            HttpServletRequest request = currentRequest();
            LoginLog row = new LoginLog();
            row.setUsername(truncate(username == null ? "" : username, USERNAME_MAX_LENGTH));
            row.setSuccess(success ? 1 : 0);
            row.setFailReason(failReason);
            String ip = ClientIpUtils.clientIp(request);
            row.setIp(ip);
            boolean privateIp = ClientIpUtils.isPrivateIp(ip);
            row.setIpLocation(privateIp ? "内网" : LOCATION_PENDING);
            row.setUserAgent(ClientIpUtils.userAgent(request, UA_MAX_LENGTH));
            row.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
            loginLogMapper.insert(row);
            // 公网 IP 异步解析归属地（百度 → ip-api 依次降级，3 秒超时），绝不阻塞登录主流程
            if (ipLocationEnabled && !privateIp && row.getId() != null) {
                Long logId = row.getId();
                taskExecutor.execute(() -> resolveAndUpdateAsync(logId, ip));
            }
        } catch (Exception e) {
            // 审计辅助数据：写入失败只记日志，绝不能让登录本身失败（用户仍可正常登录）
            LOGGER.error("登录日志写入失败（用户名[{}]，不影响登录流程）", username, e);
        }
    }

    @Override
    public PageResult<LoginLogVO> page(String username, Integer success, long page, long size) {
        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .like(username != null && !username.isBlank(), LoginLog::getUsername,
                        username == null ? null : username.trim())
                .eq(success != null, LoginLog::getSuccess, success)
                .orderByDesc(LoginLog::getId);
        Page<LoginLog> result = loginLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<LoginLogVO> rows = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result.getTotal(), rows);
    }

    /**
     * 异步解析公网 IP 归属地并回填（依次尝试百度开放数据 → ip-api.com，全部失败保留「待解析」）。
     * 失败是预期内的降级场景（第三方不稳定/保留 IP 段），按铁律 13 记 error + 完整堆栈 + IP 标识。
     */
    private void resolveAndUpdateAsync(Long logId, String ip) {
        try {
            String location = resolveOnline(ip);
            if (location == null || location.isBlank()) {
                LOGGER.error("登录日志[{}]公网IP[{}]归属地解析失败（第三方无结果），保留「待解析」", logId, ip);
                return;
            }
            LoginLog update = new LoginLog();
            update.setId(logId);
            update.setIpLocation(location);
            loginLogMapper.updateById(update);
            LOGGER.info("登录日志[{}]公网IP[{}]归属地解析成功：{}", logId, ip, location);
        } catch (Exception e) {
            LOGGER.error("登录日志[{}]公网IP[{}]归属地解析异常（保留「待解析」）", logId, ip, e);
        }
    }

    /** 依次尝试两个免费在线 IP 库，全部失败返回 null */
    private String resolveOnline(String ip) {
        String viaBaidu = resolveViaBaidu(ip);
        if (viaBaidu != null) {
            return viaBaidu;
        }
        return resolveViaIpApi(ip);
    }

    /** 百度开放数据（HTTPS、免 key）：返回省份/城市/运营商中文结果 */
    private String resolveViaBaidu(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://opendata.baidu.com/api.php?query=" + ip + "&co=&resource_id=6006&oe=utf8"))
                    .header("User-Agent", "Mozilla/5.0 (quant-platform login-log)")
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            tools.jackson.databind.JsonNode data = JsonUtils.mapper().readTree(response.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return null;
            }
            tools.jackson.databind.JsonNode first = data.get(0);
            return joinLocation(first.path("province").asText(""), first.path("city").asText(""),
                    first.path("isp").asText(""));
        } catch (Exception e) {
            LOGGER.warn("百度 IP 库解析[{}]失败：{}", ip, e.getMessage());
            return null;
        }
    }

    /** ip-api.com（免 key，免费档仅 HTTP 且限 45 次/分钟；lang=zh-CN） */
    private String resolveViaIpApi(String ip) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=status,regionName,city,isp"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            tools.jackson.databind.JsonNode root = JsonUtils.mapper().readTree(response.body());
            if (!"success".equalsIgnoreCase(root.path("status").asText())) {
                return null;
            }
            return joinLocation(root.path("regionName").asText(""), root.path("city").asText(""),
                    root.path("isp").asText(""));
        } catch (Exception e) {
            LOGGER.warn("ip-api 解析[{}]失败：{}", ip, e.getMessage());
            return null;
        }
    }

    /** 拼接归属地：省份 + 城市（城市已含在省份串尾部则不重复）+ 运营商；全空返回 null */
    private String joinLocation(String province, String city, String isp) {
        StringJoiner joiner = new StringJoiner("");
        if (province != null && !province.isBlank()) {
            joiner.add(province.trim());
        }
        if (city != null && !city.isBlank()
                && !(joiner.length() > 0 && joiner.toString().endsWith(city.trim()))) {
            joiner.add(city.trim());
        }
        if (isp != null && !isp.isBlank()) {
            joiner.add(joiner.length() > 0 ? " " + isp.trim() : isp.trim());
        }
        String result = joiner.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private LoginLogVO toVo(LoginLog row) {
        return new LoginLogVO(row.getId(), row.getUsername(), Integer.valueOf(1).equals(row.getSuccess()),
                row.getFailReason(), row.getIp(), row.getIpLocation(), row.getUserAgent(), row.getCreatedAt());
    }

    /** 当前 HTTP 请求（非 Web 线程如定时任务调用时为 null，此时 IP/UA 记空） */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
