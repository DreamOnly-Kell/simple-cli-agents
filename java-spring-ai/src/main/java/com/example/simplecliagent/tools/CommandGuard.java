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
 *
 * <p>学习要点：shell tool 能力很强，必须用可配置策略拦高危命令；
 * 子串策略优先「可配置、可解释」，不是完整安全沙箱。
 */
public class CommandGuard {

    /**
     * 默认高危模式：可通过配置覆盖；故意用易配置的子串而非复杂正则。
     *
     * <p>YAML 中 {@code shell-blocked-patterns} 为空时，由
     * {@link com.example.simplecliagent.config.AppProperties#getShellBlockedPatternsEffective()}
     * 回退到本列表。
     */
    public static final List<String> DEFAULT_BLOCKED_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            "rm -rf /",
            "rm -rf/*",
            "rm -rf ~",
            "mkfs",
            "dd if=",
            ":(){", // fork bomb 常见写法片段
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

    /**
     * @param patterns 拦截子串列表；null 或含空白项会被过滤，避免空串误匹配所有命令
     */
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
     * 将文本压缩为单空格分隔并 lower-case，便于子串比较。
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        // 与 Python " ".join(text.split()).lower() 同思路：压空白 + lower 再 contains
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * @return {@code null} 放行；否则 {@code Error:...} 可直接当 tool 结果（不 spawn）
     */
    public String check(String command) {
        if (command == null || command.isBlank()) {
            return "Error: empty command";
        }
        String normalized = normalize(command);
        for (String pattern : patterns) {
            String p = normalize(pattern);
            // 匹配用规范化串；回传保留原始 pattern/command 便于对照 YAML
            if (!p.isEmpty() && normalized.contains(p)) {
                return "Error: blocked dangerous command "
                        + "(matched policy '" + pattern + "'): " + command;
            }
        }
        return null;
    }
}
