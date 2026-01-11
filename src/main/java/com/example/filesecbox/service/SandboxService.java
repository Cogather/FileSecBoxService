package com.example.filesecbox.service;

import com.example.filesecbox.model.ExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 通用沙箱服务：提供独立于 Skill 业务的文件操作与指令执行能力
 */
@Service
public class SandboxService {

    @Value("${app.sandbox.root:/webIde/product/sandbox}")
    private String sandboxRoot;

    @Autowired
    private StorageService storageService;

    @Autowired
    private SkillExecutor skillExecutor;

    @PostConstruct
    public void init() throws IOException {
        Path root = Paths.get(sandboxRoot);
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
    }

    public Path getSandboxDir(String userId, String agentId) throws IOException {
        Path dir = Paths.get(sandboxRoot, userId, agentId);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir.toAbsolutePath().normalize();
    }

    public void writeFile(String userId, String agentId, String relativePath, String content, Integer start, Integer end) throws IOException {
        Path baseDir = getSandboxDir(userId, agentId);
        Path targetPath = baseDir.resolve(relativePath).normalize();
        
        // 安全校验
        storageService.validateScope(targetPath, baseDir, null);

        String lockKey = userId + "/" + agentId;
        storageService.writeLockedVoid(lockKey, () -> {
            if (!Files.exists(targetPath.getParent())) {
                Files.createDirectories(targetPath.getParent());
            }

            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            if (start == null && end == null) {
                storageService.writeBytes(targetPath, contentBytes, 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                List<String> lines = Files.exists(targetPath) ? 
                    Files.readAllLines(targetPath, StandardCharsets.UTF_8) : new ArrayList<>();
                
                int startIdx = (start != null) ? Math.max(0, start - 1) : 0;
                int endIdx = (end != null) ? Math.min(lines.size(), end) : lines.size();
                
                List<String> newLines = Arrays.asList(content.split("\\r?\\n"));
                
                while (lines.size() < startIdx) {
                    lines.add("");
                }
                
                List<String> updatedLines = new ArrayList<>();
                updatedLines.addAll(lines.subList(0, startIdx));
                updatedLines.addAll(newLines);
                if (endIdx < lines.size()) {
                    updatedLines.addAll(lines.subList(endIdx, lines.size()));
                }
                
                Files.write(targetPath, updatedLines, StandardCharsets.UTF_8);
            }
        });
    }

    public ExecutionResult execute(String userId, String agentId, String command, String[] args) throws Exception {
        Path workingDir = getSandboxDir(userId, agentId);
        return skillExecutor.executeInDir(workingDir, command, args);
    }

    /**
     * 读取沙箱文件内容 (支持全量/分页)
     */
    public Object readFile(String userId, String agentId, String relativePath, Integer start, Integer end) throws IOException {
        Path baseDir = getSandboxDir(userId, agentId);
        Path targetPath = baseDir.resolve(relativePath).normalize();
        
        storageService.validateScope(targetPath, baseDir, null);
        if (!Files.exists(targetPath)) throw new IOException("File not found.");

        String lockKey = userId + "/" + agentId;
        return storageService.readLocked(lockKey, () -> {
            if (start != null && end != null) {
                try (Stream<String> lines = Files.lines(targetPath, StandardCharsets.UTF_8)) {
                    return lines.skip(Math.max(0, start - 1))
                                .limit(Math.max(0, end - start + 1))
                                .collect(Collectors.toList());
                }
            } else {
                return new String(storageService.readAllBytes(targetPath), StandardCharsets.UTF_8);
            }
        });
    }

    /**
     * 列出沙箱目录下的所有文件
     */
    public List<String> listFiles(String userId, String agentId) throws IOException {
        Path baseDir = getSandboxDir(userId, agentId);
        String lockKey = userId + "/" + agentId;
        
        return storageService.readLocked(lockKey, () -> {
            List<String> fileList = new ArrayList<>();
            Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // 强制将路径分隔符统一为 /，兼容 Windows 本地验证
                    String relativePath = baseDir.relativize(file).toString().replace('\\', '/');
                    fileList.add(relativePath);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
            return fileList;
        });
    }
}

