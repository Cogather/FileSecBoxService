package com.example.filesecbox.service;

import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileService {

    private final Path rootLocation = Paths.get("/webIde/product/skill").toAbsolutePath().normalize();
    private final Map<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

    @Data
    public static class SkillMetadata {
        private String name;
        private String description;
        private String skillId;
        private String path;
    }

    public FileService() throws IOException {
        Files.createDirectories(this.rootLocation);
    }

    private ReadWriteLock getLock(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    private void validateScope(Path target, Path base) {
        if (!target.normalize().startsWith(base.normalize())) {
            throw new RuntimeException("Security Error: Access out of scope.");
        }
    }

    /**
     * 上传并安全解压 (优先 UTF-8，备选 GBK，解决 Linux 容器下 Windows ZIP 的 MALFORMED 错误)
     */
    public void storeSkillZip(String userId, String agentId, MultipartFile file) throws IOException {
        Path agentDir = rootLocation.resolve(userId).resolve(agentId);
        String lockKey = userId + "/" + agentId;
        ReadWriteLock lock = getLock(lockKey);

        lock.writeLock().lock();
        try {
            if (Files.exists(agentDir)) {
                FileSystemUtils.deleteRecursively(agentDir);
            }
            Files.createDirectories(agentDir);

            // 1. 优先尝试标准 UTF-8 编码
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
                extractZip(zis, agentDir);
            } catch (Exception e) {
                // 2. 如果 UTF-8 报 MALFORMED 错误，说明 ZIP 可能是 Windows 环境下的 GBK 编码
                try (ZipInputStream zisGbk = new ZipInputStream(file.getInputStream(), Charset.forName("GBK"))) {
                    extractZip(zisGbk, agentDir);
                } catch (Exception e2) {
                    throw new IOException("Failed to extract ZIP. Ensure it is not password protected and uses UTF-8 or GBK encoding. Error: " + e2.getMessage());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void extractZip(ZipInputStream zis, Path targetDir) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            Path entryPath = targetDir.resolve(entry.getName()).normalize();
            validateScope(entryPath, targetDir);

            if (entry.isDirectory()) {
                Files.createDirectories(entryPath);
            } else {
                Files.createDirectories(entryPath.getParent());
                Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
            }
            zis.closeEntry();
        }
    }

    /**
     * 获取技能列表 (解析 SKILL.md)
     */
    public List<SkillMetadata> getSkills(String userId, String agentId) throws IOException {
        Path agentDir = rootLocation.resolve(userId).resolve(agentId);
        if (!Files.exists(agentDir)) return Collections.emptyList();

        List<SkillMetadata> metadataList = new ArrayList<>();
        ReadWriteLock lock = getLock(userId + "/" + agentId);

        lock.readLock().lock();
        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(agentDir)) {
                for (Path skillPath : stream) {
                    if (Files.isDirectory(skillPath)) {
                        SkillMetadata meta = parseSkillMd(skillPath);
                        if (meta != null) {
                            meta.setSkillId(skillPath.getFileName().toString());
                            meta.setPath(skillPath.toString());
                            metadataList.add(meta);
                        }
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return metadataList;
    }

    /**
     * 获取指定技能下的所有文件列表 (递归)
     */
    public List<String> listSkillFiles(String userId, String agentId, String skillId) throws IOException {
        Path skillDir = rootLocation.resolve(userId).resolve(agentId).resolve(skillId);
        if (!Files.exists(skillDir)) throw new IOException("Skill not found.");

        List<String> fileList = new ArrayList<>();
        Files.walkFileTree(skillDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                fileList.add(skillDir.relativize(file).toString());
                return FileVisitResult.CONTINUE;
            }
        });
        return fileList;
    }

    /**
     * 读取文件内容 (全量或分页)
     */
    public Object readFile(String userId, String agentId, String skillId, String relativePath, Integer start, Integer end) throws IOException {
        Path skillDir = rootLocation.resolve(userId).resolve(agentId).resolve(skillId);
        Path filePath = skillDir.resolve(relativePath).normalize();
        validateScope(filePath, skillDir);

        ReadWriteLock lock = getLock(userId + "/" + agentId);
        lock.readLock().lock();
        try {
            if (start != null && end != null) {
                try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
                    return lines.skip(Math.max(0, start - 1))
                                .limit(Math.max(0, end - start + 1))
                                .collect(Collectors.toList());
                }
            } else {
                return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 编辑或创建文件 (支持全量覆盖或局部行替换)
     */
    public void writeFile(String userId, String agentId, String skillId, String relativePath, String content, Integer start, Integer end) throws IOException {
        Path skillDir = rootLocation.resolve(userId).resolve(agentId).resolve(skillId);
        Path filePath = skillDir.resolve(relativePath).normalize();
        validateScope(filePath, skillDir);

        ReadWriteLock lock = getLock(userId + "/" + agentId);
        lock.writeLock().lock();
        try {
            Files.createDirectories(filePath.getParent());
            
            if (start != null && end != null && Files.exists(filePath)) {
                // 局部替换逻辑
                List<String> allLines = new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8));
                List<String> newLines = Arrays.asList(content.split("\\r?\\n"));
                
                int startIndex = Math.max(0, start - 1);
                int endIndex = Math.min(allLines.size(), end);
                
                if (startIndex <= endIndex) {
                    // 移除旧行并插入新行
                    for (int i = 0; i < (endIndex - startIndex); i++) {
                        if (startIndex < allLines.size()) {
                            allLines.remove(startIndex);
                        }
                    }
                    allLines.addAll(startIndex, newLines);
                    Files.write(filePath, allLines, StandardCharsets.UTF_8);
                }
            } else {
                // 全量覆盖逻辑
                Files.write(filePath, content.getBytes(StandardCharsets.UTF_8), 
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private SkillMetadata parseSkillMd(Path skillPath) {
        Path mdPath = skillPath.resolve("SKILL.md");
        if (!Files.exists(mdPath)) return null;
        SkillMetadata meta = new SkillMetadata();
        try (BufferedReader reader = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase().startsWith("name:")) meta.setName(trimmed.substring(5).trim());
                else if (trimmed.toLowerCase().startsWith("description:")) meta.setDescription(trimmed.substring(12).trim());
                if (meta.getName() != null && meta.getDescription() != null) break;
            }
        } catch (IOException e) { return null; }
        return meta;
    }

    public Path getSkillDir(String userId, String agentId, String skillId) {
        return rootLocation.resolve(userId).resolve(agentId).resolve(skillId).normalize();
    }
}
