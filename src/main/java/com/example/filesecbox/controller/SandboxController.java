package com.example.filesecbox.controller;

import com.example.filesecbox.model.ApiResponse;
import com.example.filesecbox.service.SandboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用沙箱能力接口：移除 skills 前缀，提供更广泛的沙箱隔离操作
 */
@RestController
@RequestMapping("/v1/sandbox")
public class SandboxController {

    @Autowired
    private SandboxService sandboxService;

    @PutMapping("/{userId}/{agentId}/edit")
    public ResponseEntity<ApiResponse<String>> editFile(
            @PathVariable String userId,
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end,
            @RequestBody String content) {
        try {
            sandboxService.writeFile(userId, agentId, path, content, start, end);
            return ResponseEntity.ok(ApiResponse.success("Sandbox file updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Edit failed: " + e.getMessage()));
        }
    }

    @PostMapping("/{userId}/{agentId}/execute")
    public ResponseEntity<ApiResponse<String>> execute(
            @PathVariable String userId,
            @PathVariable String agentId,
            @RequestParam String command,
            @RequestBody(required = false) List<String> args) {
        try {
            String[] argsArray = args != null ? args.toArray(new String[0]) : new String[0];
            String result = sandboxService.execute(userId, agentId, command, argsArray);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Execution failed: " + e.getMessage()));
        }
    }

    /**
     * 通用沙箱文件列表
     */
    @GetMapping("/{userId}/{agentId}/files")
    public ResponseEntity<ApiResponse<List<String>>> listFiles(
            @PathVariable String userId,
            @PathVariable String agentId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(sandboxService.listFiles(userId, agentId)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 通用沙箱查看具体文件内容
     */
    @GetMapping("/{userId}/{agentId}/content")
    public ResponseEntity<ApiResponse<Object>> getFileContent(
            @PathVariable String userId,
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end) {
        try {
            return ResponseEntity.ok(ApiResponse.success(sandboxService.readFile(userId, agentId, path, start, end)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}

