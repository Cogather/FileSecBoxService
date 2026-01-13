package com.example.filesecbox.service;

import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 通用文件处理类：负责底层的物理 I/O 操作、安全校验以及全局并发锁管理。
 * 锁粒度：agentId。
 */
@Service
public class StorageService {

    private final Map<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    private ReadWriteLock getLock(String agentId) {
        return locks.computeIfAbsent(agentId, k -> new ReentrantReadWriteLock());
    }

    public <T> T readLocked(String agentId, IOCallable<T> action) throws IOException {
        ReadWriteLock lock = getLock(agentId);
        lock.readLock().lock();
        try {
            return action.call();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void writeLockedVoid(String agentId, IOVoidAction action) throws IOException {
        ReadWriteLock lock = getLock(agentId);
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 路径安全校验：确保目标路径在应用根目录下
     */
    public void validateScope(Path target, Path agentRoot) {
        Path normalizedTarget = target.normalize().toAbsolutePath();
        Path normalizedRoot = agentRoot.normalize().toAbsolutePath();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new RuntimeException("Security Error: Access out of scope. Path: " + target);
        }
    }

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
     * 精确编辑逻辑
     */
    public void preciseEdit(Path path, String oldStr, String newStr, int expected) throws IOException {
        String content = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        
        // 计算匹配次数
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(oldStr, index)) != -1) {
            count++;
            index += oldStr.length();
        }

        if (count != expected) {
            throw new RuntimeException(String.format(
                "Edit Mismatch: '%s' found %d times, but expected %d times. Please refine your search string.",
                oldStr, count, expected
            ));
        }

        String newContent = content.replace(oldStr, newStr);
        Files.write(path, newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    public interface IOCallable<T> {
        T call() throws IOException;
    }

    @FunctionalInterface
    public interface IOVoidAction {
        void run() throws IOException;
    }
}
