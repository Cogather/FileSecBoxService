package com.example.filesecbox.controller;

import com.example.filesecbox.model.ApiResponse;
import com.example.filesecbox.model.CommandRequest;
import com.example.filesecbox.model.ExecutionResult;
import com.example.filesecbox.model.FileContentResult;
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
    public ResponseEntity<ApiResponse<?>> execute(
            @PathVariable String userId,
            @PathVariable String agentId,
            @RequestBody CommandRequest request) {
        try {
            String commandLine = request.getCommand();
            if (commandLine == null || commandLine.trim().isEmpty()) {
                throw new RuntimeException("Command cannot be empty.");
            }

            String[] parts = commandLine.trim().split("\\s+");
            String cmd = parts[0];
            String[] argsArray = parts.length > 1 ? 
                    java.util.Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

            ExecutionResult result = sandboxService.execute(userId, agentId, cmd, argsArray);
            if (result.getExitCode() == 0) {
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity.status(200).body(ApiResponse.error(result));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Execution failed: " + e.getMessage()));
        }
    }

    /**
     * 通用沙箱文件列表
     */
    @GetMapping("/{userId}/{agentId}/files")
    public ResponseEntity<ApiResponse<?>> listFiles(
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
    public ResponseEntity<ApiResponse<FileContentResult>> getFileContent(
            @PathVariable String userId,
            @PathVariable String agentId,
            @RequestParam String path,
            @RequestParam(required = false) Integer start,
            @RequestParam(required = false) Integer end) {
        try {
            Object content = sandboxService.readFile(userId, agentId, path, start, end);
            return ResponseEntity.ok(ApiResponse.success(new FileContentResult(content)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(ApiResponse.error(null));
        }
    }
}

