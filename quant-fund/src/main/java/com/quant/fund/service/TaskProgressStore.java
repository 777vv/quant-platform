package com.quant.fund.service;

import java.time.Duration;

import com.quant.common.util.JsonUtils;
import com.quant.fund.dto.TaskProgressVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 异步任务进度存储（Redis，1h 过期）
 */
@Component
public class TaskProgressStore {

    private static final String KEY_PREFIX = "task:progress:";

    private final StringRedisTemplate redisTemplate;

    public TaskProgressStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(TaskProgressVO progress) {
        redisTemplate.opsForValue().set(KEY_PREFIX + progress.taskId(), JsonUtils.toJson(progress),
                Duration.ofHours(1));
    }

    public TaskProgressVO get(String taskId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        return json == null ? null : JsonUtils.fromJson(json, TaskProgressVO.class);
    }

    /**
     * 应用启动时把遗留的 RUNNING 任务标记为失败（V6.41）：进程重启会中断进行中的异步任务，
     * 若不处理，其进度键会在 Redis 里以 RUNNING 状态悬挂最多 1 小时，前端轮询会一直"同步中"。
     *
     * @return 被标记为失败的任务数（供启动日志记录）
     */
    public int failRunningOnStartup() {
        int fixed = 0;
        for (String key : redisTemplate.keys(KEY_PREFIX + "*")) {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                continue;
            }
            TaskProgressVO progress = JsonUtils.fromJson(json, TaskProgressVO.class);
            if (progress != null && TaskProgressVO.RUNNING.equals(progress.status())) {
                save(new TaskProgressVO(progress.taskId(), TaskProgressVO.FAILED, "应用重启中断",
                        progress.total(), progress.imported(), "应用重启，任务被中断，请重新发起"));
                fixed++;
            }
        }
        return fixed;
    }
}
