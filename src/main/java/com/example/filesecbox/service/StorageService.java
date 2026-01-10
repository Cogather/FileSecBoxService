package com.example.filesecbox.service;

import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 通用文件处理类：负责底层的安全校验、物理 I/O 操作以及全局并发锁管理。
 * 确保“读写不互斥、写写互斥”的线程安全机制在此层统一实现。
 */
@Service
public class StorageService {

    private final Map<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定 Key 的读写锁（通常以 userId/agentId 为锁粒度）
     */
    private ReadWriteLock getLock(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    /**
     * 在读锁保护下执行操作：实现“读读并发，读写互斥”
     * 并在读锁释放前完成数据读取，确保“快照”效果。
     */
    public <T> T readLocked(String lockKey, IOCallable<T> action) throws IOException {
        ReadWriteLock lock = getLock(lockKey);
        lock.readLock().lock();
        try {
            return action.call();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 在写锁保护下执行操作：实现“写写互斥，写读互斥”
     */
    public <T> T writeLocked(String lockKey, IOCallable<T> action) throws IOException {
        ReadWriteLock lock = getLock(lockKey);
        lock.writeLock().lock();
        try {
            return action.call();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 无返回值的写锁操作
     */
    public void writeLockedVoid(String lockKey, IOVoidAction action) throws IOException {
        ReadWriteLock lock = getLock(lockKey);
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 路径安全校验
     */
    public void validateScope(Path target, Path base1, Path base2) {
        Path normalized = target.normalize();
        boolean inBase1 = normalized.startsWith(base1.normalize());
        boolean inBase2 = (base2 != null && normalized.startsWith(base2.normalize()));
        
        if (!inBase1 && !inBase2) {
            throw new RuntimeException("Security Error: Access out of scope.");
        }
    }

    // --- 基础 I/O 方法 ---

    public byte[] readAllBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    public void writeBytes(Path path, byte[] content, OpenOption... options) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, content, options);
    }

    public void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            FileSystemUtils.deleteRecursively(path);
        }
    }

    /**
     * 定义支持抛出 IOException 的函数式接口
     */
    @FunctionalInterface
    public interface IOCallable<T> {
        T call() throws IOException;
    }

    @FunctionalInterface
    public interface IOVoidAction {
        void run() throws IOException;
    }
}
