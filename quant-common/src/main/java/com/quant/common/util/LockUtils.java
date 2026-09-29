package com.quant.common.util;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * 批量持锁句柄：close() 释放本次成功获取的全部锁（AutoCloseable，配合 try-with-resources）
     */
    public interface MultiLock extends AutoCloseable {

        @Override
        void close();
    }

    /**
     * 批量获取多把锁（waitTime=0，全部成功才返回句柄；任一被占用立即释放已获取的并返回 null）。
     * 用于"一次长任务要与多个定时任务互斥"的场景（如批量导入需与 ETF 日K/净值/估值/盘中同步互斥）。
     *
     * @param lockNames        锁名列表（自动加 lock: 前缀）
     * @param leaseTimeMinutes 每把锁的持锁上限（分钟），到期自动释放兜底
     * @return 全部获取成功返回句柄（用完必须 close）；任一失败返回 null（调用方据此跳过）
     */
    public MultiLock tryLockAll(List<String> lockNames, long leaseTimeMinutes) {
        List<RLock> acquired = new ArrayList<>();
        for (String name : lockNames) {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + name);
            boolean got = false;
            try {
                got = lock.tryLock(0, leaseTimeMinutes, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                // 中断是信号不是错误（铁律 13 唯一例外）：恢复中断位、释放已获取的锁、按"未获取到"处理
                Thread.currentThread().interrupt();
                LOGGER.warn("批量获取锁[{}]被中断，已获取的 {} 把锁全部释放", name, acquired.size());
                acquired.forEach(this::releaseQuietly);
                return null;
            }
            if (!got) {
                acquired.forEach(this::releaseQuietly);
                LOGGER.info("批量获取锁[{}]未成功（被占用），已释放其余 {} 把", LOCK_PREFIX + name, acquired.size());
                return null;
            }
            acquired.add(lock);
        }
        return () -> acquired.forEach(this::releaseQuietly);
    }

    private void releaseQuietly(Lock lock) {
        try {
            lock.unlock();
        } catch (Exception e) {
            LOGGER.error("释放锁[{}]异常（可能已过租约自动释放）", lock, e);
        }
    }
}
