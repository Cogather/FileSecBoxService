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

            // 跨用户/跨技能隔离：如果参数中包含绝对路径，必须确保它位于当前技能目录下
            // 重点检查：禁止 user_a 访问 /webIde/product/skill/user_b 的目录
            if (lowerArg.contains("/webide/product/skill")) {
                validateSkillInternalPath(arg, workingDir);
            }
        }

        // 3. 构建命令并强制锁定工作目录
        ProcessBuilder pb = new ProcessBuilder();
        List<String> cmdList = new ArrayList<>();
        cmdList.add(command);
        cmdList.addAll(Arrays.asList(args));
        pb.command(cmdList);
        
        // 核心：默认按进入到技能名称目录下执行
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
        // 常见的敏感系统路径列表
        String[] sensitivePaths = {"/etc/", "/root/", "/proc/", "/dev/", "/sys/", "/boot/", "/var/", "/home/", "/bin/", "/usr/bin/", "/usr/sbin/"};
        for (String path : sensitivePaths) {
            if (arg.contains(path)) return true;
        }
        return false;
    }

    private void validateSkillInternalPath(String arg, Path allowedDir) {
        // 提取参数中可能包含的绝对路径进行校验
        // 这里采用保守策略：如果参数字符串包含根路径，则整个参数解析后的路径必须以 allowedDir 开头
        try {
            // 简单的路径提取逻辑：寻找以 /webIde/product/skill 开头的子串
            int index = arg.toLowerCase().indexOf("/webide/product/skill");
            if (index != -1) {
                // 截取从该点开始直到空格或结束的部分作为待校验路径
                String possiblePath = arg.substring(index).split(" ")[0];
                Path targetPath = Paths.get(possiblePath).normalize();
                if (!targetPath.startsWith(allowedDir.normalize())) {
                    throw new RuntimeException("Security Error: Accessing other user's skill directory is forbidden.");
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw e;
            // 路径解析异常也视为风险
            throw new RuntimeException("Security Error: Malformed path in arguments.");
        }
    }
}
