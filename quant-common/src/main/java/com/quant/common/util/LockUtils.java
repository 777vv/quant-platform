package com.quant.common.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 分布式锁工具（Redisson）：定时任务/手动触发互斥用（技术文档 6.6）。
 * RedissonClient 由 redisson-spring-boot-starter 依据 spring.data.redis 配置自动装配。
 */
@Component
public class LockUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockUtils.class);

    private static final String LOCK_PREFIX = "lock:";

    private final RedissonClient redissonClient;

    public LockUtils(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 带锁执行：锁被占用时直接跳过（waitTime=0），返回是否实际执行
     *
     * @param lockName          锁名（自动加 lock: 前缀）
     * @param leaseTimeMinutes  持锁上限（分钟），到期自动释放兜底
     */
    public boolean runWithLock(String lockName, long leaseTimeMinutes, Runnable task) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, leaseTimeMinutes, TimeUnit.MINUTES);
            if (!locked) {
                LOGGER.info("未获取到锁[{}]，任务跳过", lockName);
                return false;
            }
            task.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("获取锁[{}]被中断", lockName);
            return false;
        } finally {
            if (locked) {
                releaseQuietly(lock);
            }
        }
    }

    private void releaseQuietly(Lock lock) {
        try {
            lock.unlock();
        } catch (Exception e) {
            LOGGER.error("释放锁[{}]异常（可能已过租约自动释放）", lock, e);
        }
    }
}
