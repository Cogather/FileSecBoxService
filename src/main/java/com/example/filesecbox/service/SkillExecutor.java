package com.example.filesecbox.service;

import com.example.filesecbox.model.ExecutionResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SkillExecutor {

    // 开放的基础指令白名单
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            "python3", "bash", "sh", "ls", "cat", "echo", "grep", "sed",
            "mkdir", "touch", "cp", "mv", "rm", "tee", "find", "chmod", "xargs"
    ));

    private static final int TIMEOUT_SECONDS = 300; // 5分钟超时

    /**
     * 在指定技能的工作目录下执行受限命令
     */
    public ExecutionResult executeInDir(Path workingDir, String command, String... args) throws Exception {
        // 1. 命令白名单校验
        if (!ALLOWED_COMMANDS.contains(command)) {
            throw new RuntimeException("Security Error: Command '" + command + "' is not allowed.");
        }

        // 2. 深度参数安全校验 (针对 Root 权限环境)
        for (String arg : args) {
            String lowerArg = arg.toLowerCase();
            
            // 1. 严格禁止路径穿越逃逸
            if (lowerArg.contains("..")) {
                throw new RuntimeException("Security Error: Path traversal '..' is strictly forbidden.");
            }

            // 2. 拦截对系统敏感目录的引用
            if (isSystemSensitivePath(lowerArg)) {
                throw new RuntimeException("Security Error: Forbidden access to system paths in arguments.");
            }

            // 3. 严格绝对路径锚定
            // 无论绝对路径是独立参数还是在 --file=/path 中，均进行校验
            if (arg.contains("/")) {
                validatePathInArgument(arg, workingDir);
            }
        }
        
        // 3. 构建命令并强制锁定工作目录
        ProcessBuilder pb = new ProcessBuilder();
        List<String> cmdList = new ArrayList<>();
        cmdList.add(command);
        cmdList.addAll(Arrays.asList(args));
        pb.command(cmdList);
        
        pb.directory(workingDir.toFile());
        // 不再合并错误流，以便分别获取 stdout 和 stderror
        pb.redirectErrorStream(false);
        
        // 4. 环境净化
        Map<String, String> env = pb.environment();
        Set<String> safeEnvVars = new HashSet<>(Arrays.asList("PATH", "LANG", "LC_ALL", "HOME", "USER", "PWD"));
        env.keySet().removeIf(key -> !safeEnvVars.contains(key));
        env.put("PATH", "/usr/local/bin:/usr/bin:/bin");

        Process process = pb.start();
        
        // 异步读取流，防止缓冲区满导致挂起
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        
        Thread outThread = new Thread(() -> captureStream(process.getInputStream(), stdoutBuilder));
        Thread errThread = new Thread(() -> captureStream(process.getErrorStream(), stderrBuilder));
        
        outThread.start();
        errThread.start();

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Execution timed out after 5 minutes.");
        }
        
        outThread.join(1000);
        errThread.join(1000);

        return new ExecutionResult(
            stdoutBuilder.toString().trim(),
            stderrBuilder.toString().trim(),
            process.exitValue()
        );
    }

    private void captureStream(InputStream is, StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException ignored) {}
    }

    private boolean isSystemSensitivePath(String arg) {
        String[] sensitivePaths = {"/etc/", "/root/", "/proc/", "/dev/", "/sys/", "/boot/", "/var/", "/home/", "/bin/", "/usr/bin/", "/usr/sbin/"};
        for (String path : sensitivePaths) {
            if (arg.contains(path)) return true;
        }
        return false;
    }

    /**
     * 校验参数中包含的路径是否合法
     */
    private void validatePathInArgument(String arg, Path workingDir) {
        // 找到第一个 / 的位置
        int slashIdx = arg.indexOf("/");
        if (slashIdx == -1) return;

        // 提取路径部分（例如从 --file=/tmp/123 提取出 /tmp/123）
        String potentialPath = arg.substring(slashIdx);
        
        // 如果路径指向的是 /webIde/product/ 目录，则必须以当前工作目录为前缀
        // 如果是系统路径，已在 isSystemSensitivePath 中拦截
        if (potentialPath.startsWith("/webIde/product/")) {
            Path targetPath = Paths.get(potentialPath).normalize();
            Path baseDir = workingDir.normalize();
            if (!targetPath.startsWith(baseDir)) {
                throw new RuntimeException("Security Error: Accessing path outside current scope: " + potentialPath);
            }
        }
    }
}
