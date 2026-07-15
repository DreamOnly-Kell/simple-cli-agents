package com.example.simplecliagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 应用级配置（前缀 {@code app.*}），写在 {@code application.yml}。
 *
 * <ul>
 *   <li>{@code workspace-root} — 文件工具沙箱根</li>
 *   <li>{@code verbose} — Logback 逻辑轨迹开关</li>
 *   <li>{@code http-log} / {@code http-log-dir} — JSONL wire 日志</li>
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
}
