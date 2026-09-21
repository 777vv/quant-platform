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
}
