package com.example.filesecbox.controller;

import com.example.filesecbox.service.FileService;
import com.example.filesecbox.service.SkillExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/v1/skills")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private SkillExecutor skillExecutor;

    /**
     * 上传技能包
     * 支持多技能 ZIP，仅覆盖同名技能目录，返回详细上传清单
     */
    @PostMapping("/{userId}/{agentId}/upload")
    public ResponseEntity<List<FileService.UploadResponse>> uploadSkill(
            @PathVariable String userId, 
            @PathVariable String agentId, 
            @RequestParam("file") MultipartFile file) {
        try {
            List<FileService.UploadResponse> responses = fileService.storeSkillZip(userId, agentId, file);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // 2. 查看技能列表 (元数据解析)
    @GetMapping("/{userId}/{agentId}/list")
    public ResponseEntity<List<FileService.SkillMetadata>> getSkillList(@PathVariable String userId, @PathVariable String agentId) {
        try {
            return ResponseEntity.ok(fileService.getSkills(userId, agentId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // 3. 查看具体技能的文件列表
    @GetMapping("/{userId}/{agentId}/{skillId}/files")
    public ResponseEntity<List<String>> listFiles(@PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId) {
        try {
            return ResponseEntity.ok(fileService.listSkillFiles(userId, agentId, skillId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // 4. 查看文件内容 (支持全量或按行读取)
    @GetMapping("/{userId}/{agentId}/{skillId}/content")
    public ResponseEntity<Object> getFileContent(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end) {
        try {
            return ResponseEntity.ok(fileService.readFile(userId, agentId, skillId, path, start, end));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // 5. 编辑/创建文件 (支持指定行范围)
    @PutMapping("/{userId}/{agentId}/{skillId}/edit")
    public ResponseEntity<String> editFile(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end,
            @RequestBody String content) {
        try {
            fileService.writeFile(userId, agentId, skillId, path, content, start, end);
            return ResponseEntity.ok("File updated.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // 6. 技能上下文内执行命令
    @PostMapping("/{userId}/{agentId}/{skillId}/execute")
    public ResponseEntity<String> execute(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String command, @RequestBody(required = false) List<String> args) {
        try {
            Path workingDir = fileService.getSkillDir(userId, agentId, skillId);
            String[] argsArray = args != null ? args.toArray(new String[0]) : new String[0];
            return ResponseEntity.ok(skillExecutor.executeInDir(workingDir, command, argsArray));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
