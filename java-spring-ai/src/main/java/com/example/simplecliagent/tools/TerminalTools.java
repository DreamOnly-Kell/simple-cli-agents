package com.example.simplecliagent.tools;

import com.example.simplecliagent.config.AppProperties;
import com.example.simplecliagent.observability.LogicalTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 终端命令工具（Spring AI {@code @Tool}）。
 *
 * <p>对照 Python {@code make_agent_tools} 中的 {@code run_command}；
 * 与 {@link FileTools} 共享同一 {@link FileWorkspace} 沙箱根（通过构造注入 FileTools）。
 *
 * <p>危险命令由 {@link CommandGuard} 按配置子串拦截；超时与拦截列表来自 {@link AppProperties}。
 */
@Component
public class TerminalTools {

    private static final Logger log = LoggerFactory.getLogger(TerminalTools.class);

    private final ShellRunner runner;
    private final LogicalTrace trace;

    /**
     * 注入 FileTools 以复用其 workspace，以及配置中的拦截策略与超时。
     *
     * <p>注意：不单独 new FileWorkspace，避免与文件 tool 根目录不一致。
     */
    public TerminalTools(FileTools fileTools, AppProperties appProperties, LogicalTrace trace) {
        // getShellBlockedPatternsEffective：YAML 列表为空时回退 DEFAULT_BLOCKED_PATTERNS
        CommandGuard guard = new CommandGuard(appProperties.getShellBlockedPatternsEffective());
        // 必须复用 FileTools 的 workspace，禁止再 new 一个根，否则 cwd 可能与文件 tool 不一致
        this.runner = new ShellRunner(
                fileTools.getWorkspace(),
                guard,
                appProperties.getShellTimeoutSeconds());
        this.trace = trace;
    }

    /**
     * 在工作区根目录执行 shell 命令（先过危险命令策略）。
     *
     * <p>{@code name = "run_command"} 与 Python tool 名对齐，便于对照学习。
     */
    @Tool(name = "run_command", description = "Execute a shell command with working directory set to the workspace root. "
            + "High-risk commands are blocked by policy. Arg command: the shell command string.")
    public String runCommand(
            @ToolParam(description = "Shell command string to execute at workspace root") String command) {
        trace.toolStart("run_command", "command=" + command);
        String result = runner.run(command);
        trace.toolEnd(result);
        log.debug("run_command resultLen={}", result == null ? 0 : result.length());
        return result;
    }
}
