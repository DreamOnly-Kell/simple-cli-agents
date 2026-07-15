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
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    private final FileWorkspace workspace;
    private final LogicalTrace trace;

    /**
     * Spring 注入：从配置构造沙箱根。
     */
    public FileTools(AppProperties appProperties, LogicalTrace trace) {
        // 每个会话共用一个沙箱根（来自 app.workspace-root）
        this.workspace = new FileWorkspace(appProperties.resolvedWorkspaceRoot());
        this.trace = trace;
    }

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
     */
    @Tool(name = "ls", description = "List files and subdirectories under a path in the workspace "
            + "(non-recursive). Arg path: relative path, default '.'.")
    public String ls(
            @ToolParam(description = "Relative path under workspace root; default '.'") String path) {
        String p = (path == null || path.isBlank()) ? "." : path;
        trace.toolStart("ls", "path=" + p);
        String result = workspace.listDir(p);
        trace.toolEnd(result);
        log.debug("ls path={} resultLen={}", p, result == null ? 0 : result.length());
        return result;
    }
}
