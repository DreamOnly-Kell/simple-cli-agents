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
 * 与 {@link FileTools} 共享同一 {@link FileWorkspace} 沙箱根。
 */
@Component
public class TerminalTools {

    private static final Logger log = LoggerFactory.getLogger(TerminalTools.class);

    private final ShellRunner runner;
    private final LogicalTrace trace;

    /**
     * 注入 FileTools 以复用其 workspace，以及配置中的拦截策略与超时。
     */
    public TerminalTools(FileTools fileTools, AppProperties appProperties, LogicalTrace trace) {
        CommandGuard guard = new CommandGuard(appProperties.getShellBlockedPatternsEffective());
        this.runner = new ShellRunner(
                fileTools.getWorkspace(),
                guard,
                appProperties.getShellTimeoutSeconds());
        this.trace = trace;
    }

    /**
     * 在工作区根目录执行 shell 命令（先过危险命令策略）。
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
