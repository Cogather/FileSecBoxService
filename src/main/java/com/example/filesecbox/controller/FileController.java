package com.example.filesecbox.controller;

import com.example.filesecbox.service.FileService;
import com.example.filesecbox.service.SkillExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/skills")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private SkillExecutor skillExecutor;

    /**
     * 上传技能包
     * 返回成功上传的技能名称字符串 (以逗号分隔)
     */
    @PostMapping("/{userId}/{agentId}/upload")
    public ResponseEntity<ApiResponse<String>> uploadSkill(
            @PathVariable String userId, 
            @PathVariable String agentId, 
            @RequestParam("file") MultipartFile file) {
        try {
            List<FileService.UploadResponse> responses = fileService.storeSkillZip(userId, agentId, file);
            String skillNames = responses.stream()
                    .map(FileService.UploadResponse::getSkillName)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.ok(ApiResponse.success(skillNames));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 查看技能列表
     */
    @GetMapping("/{userId}/{agentId}/list")
    public ResponseEntity<ApiResponse<List<FileService.SkillMetadata>>> getSkillList(@PathVariable String userId, @PathVariable String agentId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(fileService.getSkills(userId, agentId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(null));
        }
    }

    /**
     * 查看具体技能的文件列表
     */
    @GetMapping("/{userId}/{agentId}/{skillId}/files")
    public ResponseEntity<ApiResponse<List<String>>> listFiles(@PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(fileService.listSkillFiles(userId, agentId, skillId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(null));
        }
    }

    /**
     * 查看文件内容
     */
    @GetMapping("/{userId}/{agentId}/{skillId}/content")
    public ResponseEntity<ApiResponse<Object>> getFileContent(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end) {
        try {
            Object content = fileService.readFile(userId, agentId, skillId, path, start, end);
            return ResponseEntity.ok(ApiResponse.success(content));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 编辑/创建文件
     */
    @PutMapping("/{userId}/{agentId}/{skillId}/edit")
    public ResponseEntity<ApiResponse<String>> editFile(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end,
            @RequestBody String content) {
        try {
            fileService.writeFile(userId, agentId, skillId, path, content, start, end);
            return ResponseEntity.ok(ApiResponse.success("File updated."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 技能上下文内执行命令
     */
    @PostMapping("/{userId}/{agentId}/{skillId}/execute")
    public ResponseEntity<ApiResponse<String>> execute(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String command, @RequestBody(required = false) List<String> args) {
        try {
            Path workingDir = fileService.getSkillDir(userId, agentId, skillId);
            String[] argsArray = args != null ? args.toArray(new String[0]) : new String[0];
            String result = skillExecutor.executeInDir(workingDir, command, argsArray);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }
}
