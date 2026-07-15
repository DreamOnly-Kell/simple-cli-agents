package com.example.simplecliagent.config;

import com.example.simplecliagent.tools.CommandGuard;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用级配置（前缀 {@code app.*}），写在 {@code application.yml}。
 *
 * <ul>
 *   <li>{@code workspace-root} — 文件工具沙箱根</li>
 *   <li>{@code verbose} — Logback 逻辑轨迹开关</li>
 *   <li>{@code http-log} / {@code http-log-dir} — JSONL wire 日志</li>
 *   <li>{@code shell-blocked-patterns} / {@code shell-timeout-seconds} — 终端命令策略</li>
 * </ul>
 *
 * <p>Java 版不读取 {@code .env} 文件，只认 Spring 配置体系。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 文件工具沙箱根目录。
     * 默认在 {@link #resolvedWorkspaceRoot()} 中回落到 {@code user.dir}（项目启动目录）。
     */
    private Path workspaceRoot;

    /** 是否打印逻辑层 tool / 轮次日志（Logback）。 */
    private boolean verbose = true;

    /** 是否把 HTTP 请求/响应写成 jsonl（对齐 Python）。 */
    private boolean httpLog = true;

    /** HTTP jsonl 目录。 */
    private Path httpLogDir = Path.of("logs");

    /** 逻辑日志截断长度（字符）。 */
    private int verboseCharLimit = 2000;

    /**
     * 终端命令拦截子串列表（大小写不敏感）。
     * 空列表表示使用 {@link CommandGuard#DEFAULT_BLOCKED_PATTERNS}。
     */
    private List<String> shellBlockedPatterns = new ArrayList<>();

    /** {@code run_command} 超时秒数。 */
    private int shellTimeoutSeconds = 30;

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isHttpLog() {
        return httpLog;
    }

    public void setHttpLog(boolean httpLog) {
        this.httpLog = httpLog;
    }

    public Path getHttpLogDir() {
        return httpLogDir;
    }

    public void setHttpLogDir(Path httpLogDir) {
        this.httpLogDir = httpLogDir;
    }

    public int getVerboseCharLimit() {
        return verboseCharLimit;
    }

    public void setVerboseCharLimit(int verboseCharLimit) {
        this.verboseCharLimit = verboseCharLimit;
    }

    public List<String> getShellBlockedPatterns() {
        return shellBlockedPatterns;
    }

    public void setShellBlockedPatterns(List<String> shellBlockedPatterns) {
        this.shellBlockedPatterns = shellBlockedPatterns != null
                ? shellBlockedPatterns
                : new ArrayList<>();
    }

    public int getShellTimeoutSeconds() {
        return shellTimeoutSeconds;
    }

    public void setShellTimeoutSeconds(int shellTimeoutSeconds) {
        this.shellTimeoutSeconds = shellTimeoutSeconds;
    }

    /**
     * 返回生效的拦截 pattern 列表：配置为空时用代码默认高危列表。
     */
    public List<String> getShellBlockedPatternsEffective() {
        if (shellBlockedPatterns == null || shellBlockedPatterns.isEmpty()) {
            return CommandGuard.DEFAULT_BLOCKED_PATTERNS;
        }
        return shellBlockedPatterns;
    }

    /**
     * 返回绝对路径形式的工作区根，避免 cwd 变化导致沙箱漂移。
     *
     * <p>未配置时使用 {@code user.dir}（通常是项目根），避免解析到 {@code target/classes}。
     */
    public Path resolvedWorkspaceRoot() {
        Path root = workspaceRoot != null
                ? workspaceRoot
                : Path.of(System.getProperty("user.dir", "."));
        return root.toAbsolutePath().normalize();
    }

    /**
     * 返回绝对路径形式的 HTTP 日志目录。
     */
    public Path resolvedHttpLogDir() {
        return httpLogDir.toAbsolutePath().normalize();
    }

    /**
     * 生效的 shell 超时（至少 1 秒）。
     */
    public int resolvedShellTimeoutSeconds() {
        return Math.max(1, shellTimeoutSeconds);
    }
}
