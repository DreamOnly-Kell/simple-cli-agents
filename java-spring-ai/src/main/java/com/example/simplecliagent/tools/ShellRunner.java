package com.example.simplecliagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 在工作区根目录执行 shell 命令；先过 {@link CommandGuard}，再启动进程。
 *
 * <p>对齐 Python {@code ShellRunner}：cwd=workspace root，返回 exit_code + stdout/stderr。
 */
public class ShellRunner {

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
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxOutputChars = Math.max(1, maxOutputChars);
    }

    /**
     * 执行命令并返回可读结果字符串（含 exit code / stdout / stderr）。
     *
     * <p>被策略拦截时不启动进程。
     */
    public String run(String command) {
        String blocked = guard.check(command);
        if (blocked != null) {
            return blocked;
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = windows
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("sh", "-c", command);
        pb.directory(workspace.getRoot().toFile());
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
                // drain briefly after kill so we don't hang on read
                process.waitFor(2, TimeUnit.SECONDS);
                return "Error: command timed out after " + timeoutSeconds + "s: " + command;
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();

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
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "Error: failed to run command: interrupted";
        } catch (IOException e) {
            process.destroyForcibly();
            return "Error: failed to run command: " + e.getMessage();
        }
    }
}
