package com.example.filesecbox.service;

import com.example.filesecbox.model.SkillMetadata;
import com.example.filesecbox.model.UploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 沙箱 Skill 文件处理类：负责业务逻辑、分层逻辑、解压缩和元数据解析
 */
@Service
public class SkillService {

    private final Path rootLocation = Paths.get("/webIde/product/skill").toAbsolutePath().normalize();
    private final Path baselineRoot = rootLocation.resolve("baseline");
    private final Path overlayRoot = rootLocation.resolve("overlay");

    @Autowired
    private StorageService storageService;

    public SkillService() throws IOException {
        Files.createDirectories(baselineRoot);
        Files.createDirectories(overlayRoot);
    }

    /**
     * 获取目标代理目录
     */
    private Path getTargetAgentDir(String userId, String agentId, String scope) {
        if ("baseline".equalsIgnoreCase(scope)) {
            return baselineRoot.resolve(agentId);
        }
        return overlayRoot.resolve(userId).resolve(agentId);
    }

    /**
     * 上传并解压 Skill
     */
    public List<UploadResponse> storeSkillZip(String userId, String agentId, MultipartFile file, String scope) throws IOException {
        Path targetAgentDir = getTargetAgentDir(userId, agentId, scope);
        String lockKey = userId + "/" + agentId;

        return storageService.writeLocked(lockKey, () -> {
            Files.createDirectories(targetAgentDir);
            Set<String> affectedSkills = new HashSet<>();
            
            // 扫描 ZIP 编码兼容
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
                scanAffectedSkills(zis, affectedSkills);
            } catch (Exception e) {
                try (ZipInputStream zisGbk = new ZipInputStream(file.getInputStream(), Charset.forName("GBK"))) {
                    scanAffectedSkills(zisGbk, affectedSkills);
                }
            }

            // 清理并解压
            for (String skillName : affectedSkills) {
                storageService.deleteRecursively(targetAgentDir.resolve(skillName));
            }

            try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
                extractZip(zis, targetAgentDir);
            } catch (Exception e) {
                try (ZipInputStream zisGbk = new ZipInputStream(file.getInputStream(), Charset.forName("GBK"))) {
                    extractZip(zisGbk, targetAgentDir);
                } catch (Exception e2) {
                    throw new IOException("Failed to extract ZIP: " + e2.getMessage());
                }
            }

            String sourceTag = "baseline".equalsIgnoreCase(scope) ? "Baseline" : "Overlay";
            return affectedSkills.stream().map(name -> 
                new UploadResponse(name, targetAgentDir.resolve(name).toString(), "Success (" + sourceTag + ")")
            ).collect(Collectors.toList());
        });
    }

    /**
     * 获取合并后的 Skill 列表
     */
    public List<SkillMetadata> getSkills(String userId, String agentId) throws IOException {
        Map<String, SkillMetadata> mergedMap = new LinkedHashMap<>();
        String lockKey = userId + "/" + agentId;

        return storageService.readLocked(lockKey, () -> {
            loadSkillsFromDir(baselineRoot.resolve(agentId), mergedMap);
            loadSkillsFromDir(overlayRoot.resolve(userId).resolve(agentId), mergedMap);
            return new ArrayList<>(mergedMap.values());
        });
    }

    private void loadSkillsFromDir(Path dir, Map<String, SkillMetadata> map) throws IOException {
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        String skillId = p.getFileName().toString();
                        SkillMetadata meta = parseSkillMd(p);
                        if (meta != null) {
                            map.put(skillId, meta);
                        }
                    }
                }
            }
        }
    }

    /**
     * 读取文件内容 (Overlay -> Baseline)
     */
    public Object readFile(String userId, String agentId, String skillId, String relativePath, Integer start, Integer end) throws IOException {
        Path overlayDir = overlayRoot.resolve(userId).resolve(agentId).resolve(skillId);
        Path baselineDir = baselineRoot.resolve(agentId).resolve(skillId);
        
        Path filePath = overlayDir.resolve(relativePath).normalize();
        if (!Files.exists(filePath)) {
            filePath = baselineDir.resolve(relativePath).normalize();
        }
        
        storageService.validateScope(filePath, overlayDir, baselineDir);
        if (!Files.exists(filePath)) throw new IOException("File not found.");

        String lockKey = userId + "/" + agentId;
        final Path finalPath = filePath;
        return storageService.readLocked(lockKey, () -> {
            if (start != null && end != null) {
                try (Stream<String> lines = Files.lines(finalPath, StandardCharsets.UTF_8)) {
                    return lines.skip(Math.max(0, start - 1))
                                .limit(Math.max(0, end - start + 1))
                                .collect(Collectors.toList());
                }
            } else {
                return new String(storageService.readAllBytes(finalPath), StandardCharsets.UTF_8);
            }
        });
    }

    /**
     * 写入文件 (COW 逻辑)
     */
    public void writeFile(String userId, String agentId, String skillId, String relativePath, String content, Integer start, Integer end, String scope) throws IOException {
        boolean isBaseline = "baseline".equalsIgnoreCase(scope);
        Path targetDir = getTargetAgentDir(userId, agentId, scope).resolve(skillId);
        Path baselineDir = baselineRoot.resolve(agentId).resolve(skillId);
        Path filePath = targetDir.resolve(relativePath).normalize();
        
        storageService.validateScope(filePath, targetDir, null);
        String lockKey = userId + "/" + agentId;

        storageService.writeLockedVoid(lockKey, () -> {
            if (start != null && end != null) {
                // Copy-on-Write
                if (!isBaseline && !Files.exists(filePath)) {
                    Path baselineFile = baselineDir.resolve(relativePath).normalize();
                    if (Files.exists(baselineFile)) {
                        Files.copy(baselineFile, filePath);
                    } else {
                        Files.createDirectories(filePath.getParent());
                        Files.createFile(filePath);
                    }
                }
                
                List<String> allLines = Files.exists(filePath) ? 
                    new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8)) : new ArrayList<>();
                List<String> newLines = Arrays.asList(content.split("\\r?\\n"));
                
                int startIndex = Math.max(0, start - 1);
                int endIndex = Math.min(allLines.size(), end);
                
                if (startIndex <= endIndex) {
                    for (int i = 0; i < (endIndex - startIndex); i++) {
                        if (startIndex < allLines.size()) allLines.remove(startIndex);
                    }
                    allLines.addAll(startIndex, newLines);
                    Files.write(filePath, allLines, StandardCharsets.UTF_8);
                }
            } else {
                storageService.writeBytes(filePath, content.getBytes(StandardCharsets.UTF_8), 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        });
    }

    /**
     * 获取技能文件列表
     */
    public List<String> listSkillFiles(String userId, String agentId, String skillId) throws IOException {
        Path overlayDir = overlayRoot.resolve(userId).resolve(agentId).resolve(skillId);
        Path baselineDir = baselineRoot.resolve(agentId).resolve(skillId);
        Path targetDir = Files.exists(overlayDir) ? overlayDir : baselineDir;
        
        if (!Files.exists(targetDir)) throw new IOException("Skill not found.");

        String lockKey = userId + "/" + agentId;
        return storageService.readLocked(lockKey, () -> {
            List<String> fileList = new ArrayList<>();
            Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    fileList.add(targetDir.relativize(file).toString());
                    return FileVisitResult.CONTINUE;
                }
            });
            return fileList;
        });
    }

    private void scanAffectedSkills(ZipInputStream zis, Set<String> affectedSkills) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            int firstSlash = name.indexOf('/');
            if (firstSlash != -1) {
                affectedSkills.add(name.substring(0, firstSlash));
            } else if (entry.isDirectory()) {
                affectedSkills.add(name);
            }
            zis.closeEntry();
        }
    }

    private void extractZip(ZipInputStream zis, Path targetDir) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            Path entryPath = targetDir.resolve(entry.getName()).normalize();
            storageService.validateScope(entryPath, targetDir, null);
            if (entry.isDirectory()) {
                Files.createDirectories(entryPath);
            } else {
                Files.createDirectories(entryPath.getParent());
                Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
            }
            zis.closeEntry();
        }
    }

    private SkillMetadata parseSkillMd(Path skillPath) {
        Path mdPath = skillPath.resolve("SKILL.md");
        SkillMetadata meta = new SkillMetadata();
        meta.setName(skillPath.getFileName().toString());
        meta.setDescription("No description available.");
        
        if (Files.exists(mdPath)) {
            try (BufferedReader reader = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.toLowerCase().startsWith("name:")) meta.setName(trimmed.substring(5).trim());
                    else if (trimmed.toLowerCase().startsWith("description:")) meta.setDescription(trimmed.substring(12).trim());
                }
            } catch (IOException ignored) {}
        }
        return meta;
    }

    public Path getSkillDir(String userId, String agentId, String skillId) {
        Path overlayDir = overlayRoot.resolve(userId).resolve(agentId).resolve(skillId).normalize();
        if (Files.exists(overlayDir)) return overlayDir;
        return baselineRoot.resolve(agentId).resolve(skillId).normalize();
    }
}

