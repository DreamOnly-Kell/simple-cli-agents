package com.example.simplecliagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 工作区路径守卫 + 文件读写 / 列目录 / 局部编辑 / 文本搜索实现。
 *
 * <p>语义对齐 Python {@code simple_cli_agent.tools.files.FileWorkspace}：
 * <ul>
 *   <li>所有路径必须落在 {@code root} 下（应用层 path jail，非 OS 沙箱）</li>
 *   <li>失败返回以 {@code Error:} 开头的字符串，便于模型下一轮看到原因</li>
 *   <li>写文件为整文件覆盖，不做 diff</li>
 *   <li>{@link #editFile} 要求 oldStr 唯一匹配，否则不改文件</li>
 *   <li>{@link #listDir} 非递归；目录名带尾部 {@code /}</li>
 *   <li>{@link #grep} 应用层子串搜索，跳过噪音目录并限制命中数</li>
 * </ul>
 */
public class FileWorkspace {

    /** 超大文件截断阈值，避免一次 tool 结果撑爆上下文。 */
    public static final int MAX_READ_CHARS = 100_000;

    /** 默认跳过的噪音目录名（basename，用于 grep 遍历）。 */
    public static final Set<String> DEFAULT_SKIP_DIR_NAMES = Set.of(
            ".venv",
            "venv",
            "target",
            "__pycache__",
            ".git",
            "node_modules",
            "logs",
            ".mypy_cache",
            ".pytest_cache",
            ".gradle",
            "dist",
            "build"
    );

    /** grep 默认最大命中行数。 */
    public static final int DEFAULT_GREP_MAX_RESULTS = 50;

    /** grep 单行 snippet 默认最大字符数。 */
    public static final int DEFAULT_GREP_MAX_LINE_CHARS = 200;

    private final Path root;

    /**
     * @param root 沙箱根；会 normalize 成绝对路径
     */
    public FileWorkspace(Path root) {
        // 绝对化 + 去掉 . / ..；不 expanduser，也不像 Python resolve() 那样跟随符号链接
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
        // path 为绝对路径时 root.resolve(path) 结果就是 path 本身，故必须再校验前缀
        Path candidate = root.resolve(path).normalize().toAbsolutePath();
        // Path.startsWith 按路径组件判断（不是字符串 startsWith），可避免 /ws 与 /ws2 误判
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
            // 软截断：防止单文件过大占满模型上下文
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
                // 父目录不存在时递归创建
                Files.createDirectories(parent);
            }
            // 整文件覆盖；学习 demo 不做备份
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
            int len = content == null ? 0 : content.length();
            return "Wrote " + len + " chars to " + path;
        } catch (IOException e) {
            return "Error: failed to write '" + path + "': " + e.getMessage();
        }
    }

    /**
     * 唯一匹配局部编辑：{@code oldStr} 在文件中必须恰好出现 1 次才替换为 {@code newStr}。
     *
     * <p>对齐 Python {@code edit_file}：0 次或多于 1 次返回 Error 且不改文件；路径门禁同 {@link #readFile}。
     * <p>学习要点：比整文件 write 更安全的增量改法，降低模型误改其它同名片段的概率。
     *
     * @param path   相对工作区路径
     * @param oldStr 非空待替换片段
     * @param newStr 替换内容（可为 null，按空串处理，表示删除该片段）
     */
    public String editFile(String path, String oldStr, String newStr) {
        // 空串会出现在任意位置语义不清，且 count 对空串无意义；必须先拒
        if (oldStr == null || oldStr.isEmpty()) {
            return "Error: old_str must be a non-empty string";
        }
        // null → 删除该片段；与 Python new_str is None 分支一致
        if (newStr == null) {
            newStr = "";
        }
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
        final String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error: failed to read '" + path + "': " + e.getMessage();
        }
        // 非重叠计数；≠1 时绝不 write，避免「只改第一处但模型以为全改完」
        int count = countOccurrences(text, oldStr);
        if (count == 0) {
            return "Error: old_str not found in '" + path + "' (0 matches); file unchanged";
        }
        if (count > 1) {
            return "Error: old_str found " + count + " times in '" + path
                    + "' (must be unique); file unchanged";
        }
        // count==1：indexOf 即唯一处；手写拼接等价 Python replace(..., 1)
        int idx = text.indexOf(oldStr);
        String updated = text.substring(0, idx) + newStr + text.substring(idx + oldStr.length());
        try {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error: failed to write '" + path + "': " + e.getMessage();
        }
        return "Edited " + path + ": replaced 1 unique match ("
                + oldStr.length() + " -> " + newStr.length() + " chars)";
    }

    /**
     * 统计 sub 在 text 中出现次数（非重叠，与 Python {@code str.count} 一致）。
     *
     * <p>Java 没有内置 count，这里用循环 indexOf 实现，避免正则开销。
     */
    static int countOccurrences(String text, String sub) {
        if (sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(sub, from);
            if (at < 0) {
                break;
            }
            count++;
            // 非重叠：下一次从匹配末尾继续
            from = at + sub.length();
        }
        return count;
    }

    /**
     * 工作区内文本搜索（子串匹配，大小写敏感）。
     *
     * <p>对齐 Python {@code grep}：返回 {@code rel_path:line_no:snippet}；跳过噪音目录；有结果上限。
     * <p>应用层实现（不走 shell grep），路径仍受 {@link #resolve} 沙箱约束。
     *
     * @param pattern 非空子串
     * @param path    相对路径；null/空白视为 {@code "."}
     */
    public String grep(String pattern, String path) {
        return grep(pattern, path, DEFAULT_GREP_MAX_RESULTS, DEFAULT_GREP_MAX_LINE_CHARS, DEFAULT_SKIP_DIR_NAMES);
    }

    /**
     * 带上限与跳过目录配置的 grep 实现。
     *
     * @param maxResults   最大命中行数
     * @param maxLineChars 单行 snippet 最大字符
     * @param skipDirNames 要跳过的目录 basename；null/空则用默认集合
     */
    public String grep(
            String pattern,
            String path,
            int maxResults,
            int maxLineChars,
            Set<String> skipDirNames) {
        if (pattern == null || pattern.isEmpty()) {
            return "Error: pattern must be a non-empty string";
        }
        if (path == null || path.isBlank()) {
            path = ".";
        }
        // 防御性下限，避免 0/负数导致异常循环语义
        maxResults = Math.max(1, maxResults);
        maxLineChars = Math.max(20, maxLineChars);
        Set<String> skip = (skipDirNames == null || skipDirNames.isEmpty())
                ? DEFAULT_SKIP_DIR_NAMES
                : skipDirNames;

        Object resolved = resolve(path);
        if (resolved instanceof String err) {
            return err;
        }
        Path start = (Path) resolved;
        if (!Files.exists(start)) {
            return "Error: path not found: " + path;
        }

        List<String> hits = new ArrayList<>();
        // visitor 内要改 boolean，但匿名类捕获要求 effectively final → 用单元素数组
        boolean[] truncated = {false};

        try {
            if (Files.isRegularFile(start)) {
                collectGrepHits(start, pattern, maxResults, maxLineChars, hits, truncated);
            } else if (Files.isDirectory(start)) {
                final int max = maxResults;
                final int lineMax = maxLineChars;
                Files.walkFileTree(start, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (truncated[0]) {
                            return FileVisitResult.TERMINATE;
                        }
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        // start 本身即使叫 target/.venv 也要进；只剪枝其「子」目录
                        if (!dir.equals(start) && skip.contains(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (truncated[0]) {
                            return FileVisitResult.TERMINATE;
                        }
                        collectGrepHits(file, pattern, max, lineMax, hits, truncated);
                        return truncated[0] ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        // 无权限等：跳过该文件，不失败整次 grep
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                return "Error: not a file or directory: " + path;
            }
        } catch (IOException e) {
            return "Error: failed to grep under '" + path + "': " + e.getMessage();
        }

        if (hits.isEmpty()) {
            return "No matches for pattern '" + pattern + "' under " + path;
        }
        String body = String.join("\n", hits);
        if (truncated[0]) {
            body += "\n...[truncated at " + maxResults + " matches]";
        }
        return body;
    }

    /**
     * 读取单个文件并收集匹配行到 hits。
     *
     * <p>UTF-8 解码失败或超大内存风险时静默跳过该文件（与 Python 忽略二进制一致）。
     */
    private void collectGrepHits(
            Path file,
            String pattern,
            int maxResults,
            int maxLineChars,
            List<String> hits,
            boolean[] truncated) {
        if (truncated[0] || hits.size() >= maxResults) {
            truncated[0] = true;
            return;
        }
        final String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | OutOfMemoryError e) {
            // 二进制/不可读/过大：静默跳过，不让单文件拖垮整次搜索
            return;
        }
        Path rel;
        try {
            rel = root.relativize(file.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return;
        }
        String relPosix = rel.toString().replace('\\', '/');
        // \\R = 任意换行；limit=-1 保留末尾空段（与 split 默认丢弃末尾空串不同）
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains(pattern)) { // 大小写敏感、非正则
                continue;
            }
            String snippet = line.replace('\t', ' ');
            if (snippet.length() > maxLineChars) {
                snippet = snippet.substring(0, maxLineChars) + "...";
            }
            hits.add(relPosix + ":" + (i + 1) + ":" + snippet); // 行号 1-based
            if (hits.size() >= maxResults) {
                truncated[0] = true;
                return;
            }
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
        // null/空白 → 工作区根（与 Python list_dir 一致）
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
        // Files.list 仅一层，不递归
        try (Stream<Path> stream = Files.list(dir)) {
            // !isDirectory → true 的文件排后；同组按小写名
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
                // 尾部 / 仅展示，便于模型区分目录
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
