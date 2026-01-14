package com.example.filesecbox.service;

import com.example.filesecbox.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
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

@Service
public class SandboxService {

    @Value("${app.product.root.win:D:/webIde/product}")
    private String productRootWin;

    @Value("${app.product.root.linux:/webIde/product}")
    private String productRootLinux;

    private Path productRoot;

    @Autowired
    private StorageService storageService;

    @Autowired
    private SkillExecutor skillExecutor;

    @PostConstruct
    public void init() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String finalPath = os.contains("win") ? productRootWin : productRootLinux;
        
        this.productRoot = Paths.get(finalPath).toAbsolutePath().normalize();
        Files.createDirectories(productRoot);
    }

    private Path getAgentRoot(String agentId) {
        return productRoot.resolve(agentId).normalize();
    }

    /**
     * 校验并转换逻辑路径为物理路径
     */
    private Path resolveLogicalPath(String agentId, String logicalPath) {
        if (logicalPath == null) {
            throw new RuntimeException("Security Error: Path cannot be null.");
        }
        
        // 允许 skills, skills/, files, files/
        boolean isValidPrefix = logicalPath.equals("skills") || logicalPath.startsWith("skills/") ||
                               logicalPath.equals("files") || logicalPath.startsWith("files/");
        
        if (!isValidPrefix) {
            throw new RuntimeException("Security Error: Path must start with 'skills/' or 'files/'. Current path: " + logicalPath);
        }
        Path agentRoot = getAgentRoot(agentId);
        Path physicalPath = agentRoot.resolve(logicalPath).normalize();
        storageService.validateScope(physicalPath, agentRoot);
        return physicalPath;
    }

    /**
     * 1.1 上传技能
     */
    public String uploadSkillReport(String agentId, MultipartFile file) throws IOException {
        Path skillsDir = getAgentRoot(agentId).resolve("skills");
        Set<String> affectedSkills = new HashSet<>();

        storageService.writeLockedVoid(agentId, () -> {
            Files.createDirectories(skillsDir);
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
                scanAffectedSkills(zis, affectedSkills);
            } catch (Exception e) {
                try (ZipInputStream zisGbk = new ZipInputStream(file.getInputStream(), Charset.forName("GBK"))) {
                    scanAffectedSkills(zisGbk, affectedSkills);
                }
            }
            for (String skillName : affectedSkills) {
                storageService.deleteRecursively(skillsDir.resolve(skillName));
            }
            try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
                extractZip(zis, skillsDir);
            } catch (Exception e) {
                try (ZipInputStream zisGbk = new ZipInputStream(file.getInputStream(), Charset.forName("GBK"))) {
                    extractZip(zisGbk, skillsDir);
                }
            }
        });

        StringBuilder sb = new StringBuilder("Upload successful. Details:\n");
        for (String skill : affectedSkills) {
            sb.append(String.format("- Skill [%s] uploaded to [%s], Status: [Success]\n", 
                skill, skillsDir.resolve(skill).toString().replace('\\', '/')));
        }
        return sb.toString().trim();
    }

    /**
     * 1.2 获取技能描述列表
     */
    public List<SkillMetadata> getSkillList(String agentId) throws IOException {
        Path skillsDir = getAgentRoot(agentId).resolve("skills");
        if (!Files.exists(skillsDir)) return Collections.emptyList();

        return storageService.readLocked(agentId, () -> {
            List<SkillMetadata> metadataList = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        metadataList.add(parseSkillMd(entry));
                    }
                }
            }
            return metadataList;
        });
    }

    /**
     * 2.1 上传单一文件
     */
    public String uploadFile(String agentId, MultipartFile file) throws IOException {
        Path filesDir = getAgentRoot(agentId).resolve("files");
        String fileName = file.getOriginalFilename();
        Path targetPath = filesDir.resolve(fileName).normalize();
        
        storageService.validateScope(targetPath, getAgentRoot(agentId));
        
        storageService.writeLockedVoid(agentId, () -> {
            Files.createDirectories(filesDir);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        });
        return "File uploaded successfully: files/" + fileName;
    }

    /**
     * 2.2 列出目录清单
     */
    public List<String> listFiles(String agentId, String logicalPrefix) throws IOException {
        Path physicalRoot = resolveLogicalPath(agentId, logicalPrefix);
        if (!Files.exists(physicalRoot)) throw new IOException("Path not found: " + logicalPrefix);

        return storageService.readLocked(agentId, () -> {
            try (Stream<Path> stream = Files.walk(physicalRoot)) {
                return stream.filter(Files::isRegularFile)
                        .map(file -> getAgentRoot(agentId).relativize(file).toString().replace('\\', '/'))
                        .collect(Collectors.toList());
            }
        });
    }

    /**
     * 2.3 读取文件内容
     */
    public FileContentResult getContent(String agentId, String logicalPath, Integer offset, Integer limit) throws IOException {
        Path physicalPath = resolveLogicalPath(agentId, logicalPath);
        if (!Files.exists(physicalPath)) throw new IOException("Path not found: " + logicalPath);

        return storageService.readLocked(agentId, () -> {
            List<String> lines;
            try (Stream<String> lineStream = Files.lines(physicalPath, StandardCharsets.UTF_8)) {
                if (offset != null && limit != null) {
                    lines = lineStream.skip(Math.max(0, offset - 1))
                                      .limit(Math.max(0, limit))
                                      .collect(Collectors.toList());
                } else {
                    lines = lineStream.collect(Collectors.toList());
                }
            }
            String content = String.join("\n", lines);
            return new FileContentResult(content, lines);
        });
    }

    /**
     * 2.4 写入/新建文件
     */
    public String write(String agentId, WriteRequest request) throws IOException {
        Path physicalPath = resolveLogicalPath(agentId, request.getFilePath());
        storageService.writeLockedVoid(agentId, () -> {
            storageService.writeBytes(physicalPath, request.getContent().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        });
        return "Successfully created or overwritten file: " + request.getFilePath();
    }

    /**
     * 2.5 精确编辑/替换
     */
    public String edit(String agentId, EditRequest request) throws IOException {
        Path physicalPath = resolveLogicalPath(agentId, request.getFilePath());
        if (!Files.exists(physicalPath)) throw new IOException("Path not found: " + request.getFilePath());

        storageService.writeLockedVoid(agentId, () -> {
            storageService.preciseEdit(physicalPath, request.getOldString(), request.getNewString(), request.getExpectedReplacements());
        });
        return "Successfully edited file: " + request.getFilePath();
    }

    /**
     * 2.6 执行指令 (工作目录固定为应用根目录)
     */
    public ExecutionResult execute(String agentId, CommandRequest request) throws Exception {
        Path agentRoot = getAgentRoot(agentId);
        if (!Files.exists(agentRoot)) Files.createDirectories(agentRoot);

        String commandLine = request.getCommand().trim();
        String[] parts = commandLine.split("\\s+");
        String cmd = parts[0];
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        return skillExecutor.executeInDir(agentRoot, cmd, args);
    }

    // --- 内部辅助方法 ---

    private void scanAffectedSkills(ZipInputStream zis, Set<String> skills) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            int slash = name.indexOf('/');
            if (slash != -1) skills.add(name.substring(0, slash));
            else if (entry.isDirectory()) skills.add(name);
            zis.closeEntry();
        }
    }

    private void extractZip(ZipInputStream zis, Path targetDir) throws IOException {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            Path entryPath = targetDir.resolve(entry.getName()).normalize();
            if (entry.isDirectory()) Files.createDirectories(entryPath);
            else {
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
                boolean inYaml = false;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.equals("---")) { inYaml = !inYaml; continue; }
                    String lower = trimmed.toLowerCase();
                    if (lower.startsWith("name:") || lower.startsWith("name：")) {
                        meta.setName(trimmed.substring(trimmed.indexOf(trimmed.contains(":")?":":"：")+1).trim());
                    } else if (lower.startsWith("description:") || lower.startsWith("description：")) {
                        meta.setDescription(trimmed.substring(trimmed.indexOf(trimmed.contains(":")?":":"：")+1).trim());
                    }
                }
            } catch (IOException ignored) {}
        }
        return meta;
    }
}
