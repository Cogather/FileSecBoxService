package com.example.filesecbox.controller;

import com.example.filesecbox.model.ApiResponse;
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
            List<SkillService.UploadResponse> responses = skillService.storeSkillZip(userId, agentId, file, scope);
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
    public ResponseEntity<ApiResponse<List<SkillService.SkillMetadata>>> getSkillList(
            @PathVariable String userId, @PathVariable String agentId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.getSkills(userId, agentId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(null));
        }
    }

    // 3. 执行技能命令
    @PostMapping("/{userId}/{agentId}/{skillId}/execute")
    public ResponseEntity<ApiResponse<String>> execute(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String command, @RequestBody(required = false) List<String> args) {
        try {
            Path workingDir = skillService.getSkillDir(userId, agentId, skillId);
            String[] argsArray = args != null ? args.toArray(new String[0]) : new String[0];
            String result = skillExecutor.executeInDir(workingDir, command, argsArray);
            return ResponseEntity.ok(ApiResponse.success(result));
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
    public ResponseEntity<ApiResponse<Object>> getFileContent(
            @PathVariable String userId, @PathVariable String agentId, @PathVariable String skillId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end) {
        try {
            return ResponseEntity.ok(ApiResponse.success(skillService.readFile(userId, agentId, skillId, path, start, end)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}

