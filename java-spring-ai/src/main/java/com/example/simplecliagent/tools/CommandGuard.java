package com.example.simplecliagent.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 根据配置的 pattern 列表拦截危险 shell 命令。
 *
 * <p>对齐 Python {@code simple_cli_agent.tools.shell.CommandGuard}：
 * 将 command 与 pattern 都压缩空白并 lower 后，若 pattern 为 command 的子串则拦截。
 */
public class CommandGuard {

    /**
     * 默认高危模式：可通过配置覆盖；故意用易配置的子串而非复杂正则。
     */
    public static final List<String> DEFAULT_BLOCKED_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            "rm -rf /",
            "rm -rf/*",
            "rm -rf ~",
            "mkfs",
            "dd if=",
            ":(){",
            "shutdown",
            "reboot",
            "halt",
            "poweroff",
            "curl|sh",
            "curl | sh",
            "wget|sh",
            "wget | sh",
            "sudo ",
            "chmod -r 777 /",
            "> /dev/sd"
    ));

    private final List<String> patterns;

    public CommandGuard(List<String> patterns) {
        List<String> cleaned = new ArrayList<>();
        if (patterns != null) {
            for (String p : patterns) {
                if (p != null && !p.isBlank()) {
                    cleaned.add(p);
                }
            }
        }
        this.patterns = List.copyOf(cleaned);
    }

    public List<String> getPatterns() {
        return patterns;
    }

    /**
     * 将文本压缩为单空格分隔并 lower-case。
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * 检查命令是否被策略拦截。
     *
     * @return {@code null} 表示允许执行；否则返回以 {@code Error:} 开头的拦截原因
     */
    public String check(String command) {
        if (command == null || command.isBlank()) {
            return "Error: empty command";
        }
        String normalized = normalize(command);
        for (String pattern : patterns) {
            String p = normalize(pattern);
            if (!p.isEmpty() && normalized.contains(p)) {
                return "Error: blocked dangerous command "
                        + "(matched policy '" + pattern + "'): " + command;
            }
        }
        return null;
    }
}
