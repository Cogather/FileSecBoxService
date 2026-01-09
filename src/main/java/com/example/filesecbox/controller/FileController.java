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

    // 1. 上传技能包
    @PostMapping("/{userId}/{agentId}/upload")
    public ResponseEntity<String> uploadSkill(@PathVariable String userId, @PathVariable String agentId, @RequestParam("file") MultipartFile file) {
        try {
            fileService.storeSkillZip(userId, agentId, file);
            return ResponseEntity.ok("Skill uploaded and extracted.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
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
