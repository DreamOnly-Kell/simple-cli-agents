package com.example.simplecliagent.tools;

import com.example.simplecliagent.config.AppProperties;
import com.example.simplecliagent.observability.LogicalTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 暴露给模型的文件工具（Spring AI {@code @Tool}）。
 *
 * <p>对照 Python {@code make_file_tools} / {@code ls}：这里用注解方法代替 StructuredTool；
 * 真实磁盘逻辑仍委托 {@link FileWorkspace}。
 *
 * <p>方法名/描述会进入请求体顶层 {@code tools} schema，不是塞进 content。
 * <p>每个方法在执行前后打 {@link LogicalTrace}，对齐 Python 控制台 {@code >>> tool} 轨迹。
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    private final FileWorkspace workspace;
    private final LogicalTrace trace;

    /**
     * Spring 注入：从配置构造沙箱根。
     *
     * <p>{@link TerminalTools} 会通过 {@link #getWorkspace()} 复用同一 workspace，
     * 保证文件 tool 与 shell cwd 指向同一根目录。
     */
    public FileTools(AppProperties appProperties, LogicalTrace trace) {
        // 全进程共用一个 workspace；TerminalTools 通过 getWorkspace() 复用，保证 cwd/根一致
        this.workspace = new FileWorkspace(appProperties.resolvedWorkspaceRoot());
        this.trace = trace;
    }

    /** 供 TerminalTools 等复用同一 FileWorkspace 实例。 */
    public FileWorkspace getWorkspace() {
        return workspace;
    }

    /**
     * 读取工作区内文本文件。
     *
     * <p>模型通过 tool_calls 选中本方法；框架执行后把返回值作为 tool 消息回灌。
     */
    @Tool(description = "Read the full text content of a file inside the workspace. "
            + "Arg path: relative path from workspace root.")
    public String readFile(
            @ToolParam(description = "Relative path from workspace root") String path) {
        // 逻辑轨迹（Logback）——对齐 Python console >>> tool
        trace.toolStart("read_file", "path=" + path);
        String result = workspace.readFile(path);
        trace.toolEnd(result);
        log.debug("read_file path={} resultLen={}", path, result == null ? 0 : result.length());
        return result;
    }

    /**
     * 创建或覆盖写入工作区内文本文件。
     */
    @Tool(description = "Create or overwrite a text file inside the workspace. "
            + "Args: path (relative), content (full file text to write).")
    public String writeFile(
            @ToolParam(description = "Relative path from workspace root") String path,
            @ToolParam(description = "Full file text to write (overwrite)") String content) {
        trace.toolStart("write_file", "path=" + path + ", contentChars="
                + (content == null ? 0 : content.length()));
        String result = workspace.writeFile(path, content);
        trace.toolEnd(result);
        log.debug("write_file path={} ok={}", path, result);
        return result;
    }

    /**
     * 列出工作区内目录条目（非递归）。
     *
     * <p>对齐 Python {@code ls} tool；{@code name = "ls"} 保证模型看到的工具名与 Python 一致。
     */
    @Tool(name = "ls", description = "List files and subdirectories under a path in the workspace "
            + "(non-recursive). Arg path: relative path, default '.'.")
    public String ls(
            @ToolParam(description = "Relative path under workspace root; default '.'") String path) {
        // Spring 可能把未填参数绑成 null；与 FileWorkspace.listDir 的空串处理对齐
        String p = (path == null || path.isBlank()) ? "." : path;
        trace.toolStart("ls", "path=" + p);
        String result = workspace.listDir(p);
        trace.toolEnd(result);
        log.debug("ls path={} resultLen={}", p, result == null ? 0 : result.length());
        return result;
    }

    /**
     * 唯一匹配局部编辑（对齐 Python {@code edit_file}）。
     *
     * <p>oldStr 必须恰好出现一次；否则返回 Error 且不改文件。
     */
    @Tool(name = "edit_file", description = "Safely edit a file by replacing exactly one occurrence of old_str with new_str. "
            + "Fails without modifying the file if old_str is missing or appears more than once. "
            + "Args: path (relative), old_str, new_str.")
    public String editFile(
            @ToolParam(description = "Relative path from workspace root") String path,
            @ToolParam(description = "Exact substring that must appear once") String oldStr,
            @ToolParam(description = "Replacement text") String newStr) {
        // 轨迹只记长度：old/new 可能含密钥或整文件片段，不宜进 Logback
        trace.toolStart("edit_file", "path=" + path
                + ", oldLen=" + (oldStr == null ? 0 : oldStr.length())
                + ", newLen=" + (newStr == null ? 0 : newStr.length()));
        // 唯一匹配等业务规则全部在 FileWorkspace，这里只做 @Tool 边界
        String result = workspace.editFile(path, oldStr, newStr);
        trace.toolEnd(result);
        log.debug("edit_file path={} ok={}", path, result);
        return result;
    }

    /**
     * 工作区内文本搜索（对齐 Python {@code grep}）。
     *
     * <p>应用层子串搜索，不走 shell；返回 {@code rel_path:line_no:snippet}。
     */
    @Tool(name = "grep", description = "Search for a text pattern under a workspace path (application-level, not shell). "
            + "Returns lines as rel_path:line_no:snippet. Skips .venv/target/__pycache__/etc. "
            + "Args: pattern (required), path (relative, default '.').")
    public String grep(
            @ToolParam(description = "Text pattern to search for") String pattern,
            @ToolParam(description = "Relative path under workspace; default '.'") String path) {
        String p = (path == null || path.isBlank()) ? "." : path;
        trace.toolStart("grep", "pattern=" + pattern + ", path=" + p);
        String result = workspace.grep(pattern, p);
        trace.toolEnd(result);
        log.debug("grep path={} resultLen={}", p, result == null ? 0 : result.length());
        return result;
    }
}
