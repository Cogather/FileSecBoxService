package com.example.filesecbox.controller;

import com.example.filesecbox.model.ApiResponse;
import com.example.filesecbox.model.CommandRequest;
import com.example.filesecbox.model.ExecutionResult;
import com.example.filesecbox.model.FileContentResult;
import com.example.filesecbox.model.SkillMetadata;
import com.example.filesecbox.model.UploadResponse;
import com.example.filesecbox.service.SkillExecutor;
import com.example.filesecbox.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 沙箱 Skill 综合接口：包含上传、列表、执行及文件查看
 */
@RestController
@RequestMapping("/v1/skills")
public class SkillController {

    @Autowired
    private SkillService skillService;

    @Autowired
    private SkillExecutor skillExecutor;

    // 1. 上传技能
    @PostMapping("/{userId}/{agentId}/upload")
    public ResponseEntity<ApiResponse<String>> uploadSkill(
            @PathVariable String userId, 
            @PathVariable String agentId, 
            @RequestParam(required = false, defaultValue = "overlay") String scope,
            @RequestParam("file") MultipartFile file) {
        try {
            List<UploadResponse> responses = skillService.storeSkillZip(userId, agentId, file, scope);
            String details = responses.stream()
                    .map(r -> String.format("skill '%s' to '%s'", r.getSkillName(), r.getUploadPath()))
                    .collect(Collectors.joining(", "));
            return ResponseEntity.ok(ApiResponse.success("Upload successful: " + details));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    // 2. 获取技能列表
    @GetMapping("/{userId}/{agentId}/list")
    public ResponseEntity<ApiResponse<List<SkillMetadata>>> getSkillList(
            @PathVariable String userId, @PathVariable String agentId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.getSkills(userId, agentId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(null));
        }
    }

    // 3. 执行技能命令
    @PostMapping("/{userId}/{agentId}/{skillId}/execute")
    public ResponseEntity<ApiResponse<?>> execute(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestBody CommandRequest request) {
        try {
            String commandLine = request.getCommand();
            if (commandLine == null || commandLine.trim().isEmpty()) {
                throw new RuntimeException("Command cannot be empty.");
            }
            
            Path workingDir = skillService.getSkillDir(userId, agentId, skillId);
            String[] parts = commandLine.trim().split("\\s+");
            String cmd = parts[0];
            String[] argsArray = parts.length > 1 ? 
                    java.util.Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

            ExecutionResult result = skillExecutor.executeInDir(workingDir, cmd, argsArray);
            if (result.getExitCode() == 0) {
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity.status(200).body(ApiResponse.error(result));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    // 4. 查看技能文件清单
    @GetMapping("/{userId}/{agentId}/{skillId}/files")
    public ResponseEntity<ApiResponse<List<String>>> listFiles(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.listSkillFiles(userId, agentId, skillId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(null));
        }
    }

    // 5. 查看具体文件内容
    @GetMapping("/{userId}/{agentId}/{skillId}/content")
    public ResponseEntity<ApiResponse<FileContentResult>> getFileContent(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end) {
        try {
            Object content = skillService.readFile(userId, agentId, skillId, path, start, end);
            return ResponseEntity.ok(ApiResponse.success(new FileContentResult(content)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(ApiResponse.error(null));
        }
    }
}

