package com.example.simplecliagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 在工作区根目录执行 shell 命令；先过 {@link CommandGuard}，再启动进程。
 *
 * <p>对齐 Python {@code ShellRunner}：cwd=workspace root，返回 exit_code + stdout/stderr。
 *
 * <p>与 FileWorkspace 的关系：只复用 workspace root 作为 cwd；
 * 命令本身仍可访问 cwd 外路径（应用层 path jail 管文件 tool，不管 shell——v1 明确取舍）。
 */
public class ShellRunner {

    /** tool 结果过长会占满模型上下文，默认 50k 字符软截断。 */
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 50_000;

    private final FileWorkspace workspace;
    private final CommandGuard guard;
    private final int timeoutSeconds;
    private final int maxOutputChars;

    public ShellRunner(FileWorkspace workspace, CommandGuard guard, int timeoutSeconds) {
        this(workspace, guard, timeoutSeconds, DEFAULT_MAX_OUTPUT_CHARS);
    }

    public ShellRunner(
            FileWorkspace workspace,
            CommandGuard guard,
            int timeoutSeconds,
            int maxOutputChars) {
        this.workspace = workspace;
        this.guard = guard;
        // 超时至少 1 秒，避免 0 导致立即超时
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxOutputChars = Math.max(1, maxOutputChars);
    }

    /**
     * 执行命令并返回可读结果字符串（含 exit code / stdout / stderr）。
     *
     * <p>被策略拦截时不启动进程。
     */
    public String run(String command) {
        // 先策略：拦截则不 start 进程
        String blocked = guard.check(command);
        if (blocked != null) {
            return blocked;
        }

        // 模型给的是整行命令串；Windows 走 cmd /c，其它走 sh -c（对齐 Python shell=True）
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = windows
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("sh", "-c", command);
        // cwd 钉死工作区根（与文件 tool 根一致），与 JVM 启动目录无关
        pb.directory(workspace.getRoot().toFile());
        // false：stdout/stderr 分开读，再拼成与 Python 相同的固定格式
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return "Error: failed to run command: " + e.getMessage();
        }

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // 给 OS 一点时间收尸，避免随后 readAllBytes 阻塞
                process.waitFor(2, TimeUnit.SECONDS);
                // 超时不返回部分输出，避免半截结果误导模型
                return "Error: command timed out after " + timeoutSeconds + "s: " + command;
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

            // 非 0 也当正常 tool 结果（不是异常），模型读 exit_code 再决策
            String combined = "exit_code=" + exitCode + "\n"
                    + "--- stdout ---\n" + stdout
                    + "--- stderr ---\n" + stderr;
            if (combined.length() > maxOutputChars) {
                int total = combined.length();
                combined = combined.substring(0, maxOutputChars)
                        + "\n...[truncated, total " + total + " chars]";
            }
            return combined;
        } catch (InterruptedException e) {
            // 恢复中断标志，避免吞掉上层中断语义
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "Error: failed to run command: interrupted";
        } catch (IOException e) {
            process.destroyForcibly();
            return "Error: failed to run command: " + e.getMessage();
        }
    }
}
