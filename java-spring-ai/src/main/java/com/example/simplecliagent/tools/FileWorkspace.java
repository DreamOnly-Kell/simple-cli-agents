package com.example.simplecliagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 工作区路径守卫 + 文件读写 / 列目录实现。
 *
 * <p>语义对齐 Python {@code simple_cli_agent.tools.files.FileWorkspace}：
 * <ul>
 *   <li>所有路径必须落在 {@code root} 下（应用层 path jail，非 OS 沙箱）</li>
 *   <li>失败返回以 {@code Error:} 开头的字符串，便于模型下一轮看到原因</li>
 *   <li>写文件为整文件覆盖，不做 diff</li>
 *   <li>{@link #listDir} 非递归；目录名带尾部 {@code /}</li>
 * </ul>
 */
public class FileWorkspace {

    /** 超大文件截断阈值，避免一次 tool 结果撑爆上下文。 */
    public static final int MAX_READ_CHARS = 100_000;

    private final Path root;

    /**
     * @param root 沙箱根；会 normalize 成绝对路径
     */
    public FileWorkspace(Path root) {
        // toAbsolutePath + normalize 对应 Python 的 expanduser().resolve()
        this.root = root.toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 将用户/模型给出的 path 解析为工作区内路径。
     *
     * @return 合法时为 Path；逃逸时为 Error 字符串（与 Python union 返回同思路）
     */
    public Object resolve(String path) {
        // 绝对 path 时 resolve 仍以该绝对路径为准，故后面必须 startsWith 检查
        Path candidate = root.resolve(path).normalize().toAbsolutePath();
        if (!candidate.startsWith(root)) {
            return "Error: path escape denied for '" + path + "' (must stay under " + root + ")";
        }
        return candidate;
    }

    /**
     * 读取工作区内 UTF-8 文本文件。
     *
     * @param path 相对工作区的路径
     * @return 文件正文，或 Error: 开头的说明
     */
    public String readFile(String path) {
        Object resolved = resolve(path);
        if (resolved instanceof String err) {
            return err;
        }
        Path file = (Path) resolved;
        if (!Files.exists(file)) {
            return "Error: file not found: " + path;
        }
        if (!Files.isRegularFile(file)) {
            return "Error: not a file: " + path;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.length() > MAX_READ_CHARS) {
                return text.substring(0, MAX_READ_CHARS)
                        + "\n\n...[truncated, total " + text.length()
                        + " chars, showing first " + MAX_READ_CHARS + "]";
            }
            return text;
        } catch (IOException e) {
            return "Error: failed to read '" + path + "': " + e.getMessage();
        }
    }

    /**
     * 在工作区内创建或覆盖写入 UTF-8 文本。
     *
     * @param path    相对路径
     * @param content 完整文件内容（覆盖语义）
     * @return 成功摘要，或 Error: 开头说明
     */
    public String writeFile(String path, String content) {
        Object resolved = resolve(path);
        if (resolved instanceof String err) {
            return err;
        }
        Path file = (Path) resolved;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
            int len = content == null ? 0 : content.length();
            return "Wrote " + len + " chars to " + path;
        } catch (IOException e) {
            return "Error: failed to write '" + path + "': " + e.getMessage();
        }
    }

    /**
     * 列出工作区内某目录下的文件/子目录名（非递归）。
     *
     * <p>对齐 Python {@code list_dir}：目录名带尾部 {@code /}；目录优先、名称大小写不敏感排序。
     *
     * @param path 相对路径；null/空白视为 {@code "."}
     * @return 每行一个条目，或 Error: 开头说明
     */
    public String listDir(String path) {
        if (path == null || path.isBlank()) {
            path = ".";
        }
        Object resolved = resolve(path);
        if (resolved instanceof String err) {
            return err;
        }
        Path dir = (Path) resolved;
        if (!Files.exists(dir)) {
            return "Error: path not found: " + path;
        }
        if (!Files.isDirectory(dir)) {
            return "Error: not a directory: " + path;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> entries = stream
                    .sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
            if (entries.isEmpty()) {
                return "(empty directory) " + path;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                Path entry = entries.get(i);
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    name = name + "/";
                }
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(name);
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error: failed to list '" + path + "': " + e.getMessage();
        }
    }
}
