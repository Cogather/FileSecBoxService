package com.example.filesecbox.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    public String executeInDir(Path workingDir, String command, String... args) throws Exception {
        // 1. 命令白名单校验
        if (!ALLOWED_COMMANDS.contains(command)) {
            throw new RuntimeException("Security Error: Command '" + command + "' is not allowed.");
        }

        // 2. 深度参数安全校验 (针对 Root 权限环境)
        for (String arg : args) {
            String lowerArg = arg.toLowerCase();
            
            // 严格禁止路径穿越逃逸
            if (lowerArg.contains("..")) {
                throw new RuntimeException("Security Error: Path traversal '..' is strictly forbidden.");
            }

            // 拦截对系统敏感目录的任何形式引用 (即使是在命令字符串中间)
            if (isSystemSensitivePath(lowerArg)) {
                throw new RuntimeException("Security Error: Forbidden access to system paths in arguments.");
            }

            // 严格绝对路径锚定：若参数以 / 开头，强制要求其物理路径位于当前技能目录下
            if (arg.startsWith("/")) {
                validateAbsoluteSkillPath(arg, workingDir);
            }
        }

        // 3. 构建命令并强制锁定工作目录
        ProcessBuilder pb = new ProcessBuilder();
        List<String> cmdList = new ArrayList<>();
        cmdList.add(command);
        cmdList.addAll(Arrays.asList(args));
        pb.command(cmdList);
        
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        
        // 4. 环境净化
        Map<String, String> env = pb.environment();
        Set<String> safeEnvVars = new HashSet<>(Arrays.asList("PATH", "LANG", "LC_ALL", "HOME", "USER", "PWD"));
        env.keySet().removeIf(key -> !safeEnvVars.contains(key));
        env.put("PATH", "/usr/local/bin:/usr/bin:/bin");

        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Execution timed out after 5 minutes.");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.exitValue();
            return "Exit Code: " + exitCode + "\nOutput:\n" + output;
        }
    }

    private boolean isSystemSensitivePath(String arg) {
        String[] sensitivePaths = {"/etc/", "/root/", "/proc/", "/dev/", "/sys/", "/boot/", "/var/", "/home/", "/bin/", "/usr/bin/", "/usr/sbin/"};
        for (String path : sensitivePaths) {
            if (arg.contains(path)) return true;
        }
        return false;
    }

    /**
     * 校验绝对路径是否锚定在当前技能目录内 (非模糊匹配)
     */
    private void validateAbsoluteSkillPath(String arg, Path workingDir) {
        try {
            // 获取第一个空格前的路径部分（防止参数拼接）
            String pathPart = arg.split(" ")[0];
            Path targetPath = Paths.get(pathPart).normalize();
            Path baseDir = workingDir.normalize();
            
            // 使用 Path.startsWith 进行目录级组件匹配
            if (!targetPath.startsWith(baseDir)) {
                throw new RuntimeException("Security Error: Absolute path '" + arg + "' is outside the allowed skill directory.");
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw e;
            throw new RuntimeException("Security Error: Invalid path format in arguments.");
        }
    }
}
