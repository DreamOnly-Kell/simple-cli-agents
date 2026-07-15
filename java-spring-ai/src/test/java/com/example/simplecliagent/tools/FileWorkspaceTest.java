package com.example.simplecliagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件沙箱单测：不依赖 LLM，对齐 Python tests/test_files.py + list_dir。
 */
class FileWorkspaceTest {

    @TempDir
    Path tempDir;

    private FileWorkspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new FileWorkspace(tempDir);
    }

    @Test
    void writeAndReadRoundTrip() throws Exception {
        String msg = workspace.writeFile("hello.txt", "hi there");
        assertTrue(msg.contains("hello.txt"));
        assertEquals("hi there", Files.readString(tempDir.resolve("hello.txt")));
        assertEquals("hi there", workspace.readFile("hello.txt"));
    }

    @Test
    void rejectsPathEscape() throws Exception {
        Path outside = tempDir.getParent().resolve("secret-outside.txt");
        Files.writeString(outside, "nope");
        String result = workspace.readFile("../secret-outside.txt");
        assertTrue(result.toLowerCase().contains("error") || result.toLowerCase().contains("denied")
                || result.toLowerCase().contains("escape"));
        assertTrue(!result.contains("nope"));
    }

    @Test
    void writeCreatesParentDirs() {
        workspace.writeFile("a/b/c.txt", "nested");
        assertEquals("nested", workspace.readFile("a/b/c.txt"));
    }

    @Test
    void readMissingFileReturnsError() {
        String result = workspace.readFile("missing.txt");
        assertTrue(result.toLowerCase().contains("error") || result.toLowerCase().contains("not found"));
    }

    @Test
    void listDirListsWorkspaceFiles() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "x");
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("b.txt"), "y");

        String out = workspace.listDir(".");
        assertTrue(out.contains("a.txt"));
        assertTrue(out.contains("sub/") || out.contains("sub"));

        String outSub = workspace.listDir("sub");
        assertTrue(outSub.contains("b.txt"));
    }

    @Test
    void listDirRejectsEscape() {
        String result = workspace.listDir("../");
        String lower = result.toLowerCase();
        assertTrue(lower.contains("error") || lower.contains("denied") || lower.contains("escape"));
    }

    @Test
    void listDirEmptyDirectory() {
        String result = workspace.listDir(".");
        assertTrue(result.contains("empty") || result.isBlank() || result.startsWith("(empty"));
    }

    @Test
    void listDirMissingPathReturnsError() {
        String result = workspace.listDir("no-such-dir");
        assertTrue(result.toLowerCase().contains("error") || result.toLowerCase().contains("not found"));
    }

    @Test
    void editFileUniqueMatchSucceeds() throws Exception {
        Path target = tempDir.resolve("sample.py");
        Files.writeString(target, "def hello():\n    return 1\n");
        String msg = workspace.editFile("sample.py", "return 1", "return 2");
        assertTrue(!msg.toLowerCase().contains("error"));
        assertEquals("def hello():\n    return 2\n", Files.readString(target));
        assertEquals("def hello():\n    return 2\n", workspace.readFile("sample.py"));
    }

    @Test
    void editFileZeroMatchesUnchanged() throws Exception {
        Path target = tempDir.resolve("a.txt");
        String original = "alpha beta gamma";
        Files.writeString(target, original);
        String msg = workspace.editFile("a.txt", "missing-token", "x");
        assertTrue(msg.toLowerCase().contains("error"));
        assertTrue(msg.contains("0") || msg.toLowerCase().contains("not found"));
        assertEquals(original, Files.readString(target));
    }

    @Test
    void editFileMultipleMatchesUnchanged() throws Exception {
        Path target = tempDir.resolve("b.txt");
        String original = "xx foo yy foo zz";
        Files.writeString(target, original);
        String msg = workspace.editFile("b.txt", "foo", "bar");
        assertTrue(msg.toLowerCase().contains("error"));
        assertTrue(msg.contains("2") || msg.toLowerCase().contains("times")
                || msg.toLowerCase().contains("multiple"));
        assertEquals(original, Files.readString(target));
    }

    @Test
    void editFilePathEscapeRejected() throws Exception {
        Path outside = tempDir.getParent().resolve("outside_edit.txt");
        Files.writeString(outside, "secret");
        String msg = workspace.editFile("../outside_edit.txt", "secret", "hacked");
        String lower = msg.toLowerCase();
        assertTrue(lower.contains("error") || lower.contains("denied") || lower.contains("escape"));
        assertEquals("secret", Files.readString(outside));
    }

    @Test
    void editFileEmptyOldStrRejected() throws Exception {
        Path target = tempDir.resolve("c.txt");
        Files.writeString(target, "keep");
        String msg = workspace.editFile("c.txt", "", "x");
        assertTrue(msg.toLowerCase().contains("error"));
        assertEquals("keep", Files.readString(target));
    }

    @Test
    void grepFindsKnownTextWithRelPathAndLine() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("app.py"), "def main():\n    print('needle-xyz')\n");
        String out = workspace.grep("needle-xyz", ".");
        assertTrue(out.contains("src/app.py:2:") || out.contains("src\\app.py:2:"));
        assertTrue(out.contains("needle-xyz"));
        assertTrue(!out.toLowerCase().startsWith("error"));
    }

    @Test
    void grepNoMatches() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello");
        String out = workspace.grep("not-present-zzz", ".");
        String lower = out.toLowerCase();
        assertTrue(lower.contains("no matches") || lower.contains("not found") || lower.contains("0"));
    }

    @Test
    void grepPathEscapeRejected() throws Exception {
        Path outside = tempDir.getParent().resolve("outside_grep.txt");
        Files.writeString(outside, "secret-needle");
        String out = workspace.grep("secret-needle", "../");
        String lower = out.toLowerCase();
        assertTrue(lower.contains("error") || lower.contains("denied") || lower.contains("escape"));
        assertTrue(!out.contains("outside_grep"));
    }

    @Test
    void grepSkipsNoiseDirs() throws Exception {
        Files.writeString(tempDir.resolve("keep.txt"), "visible-token");
        for (String noise : List.of(".venv", "target", "__pycache__", "logs")) {
            Path d = tempDir.resolve(noise);
            Files.createDirectories(d);
            Files.writeString(d.resolve("hidden.txt"), "visible-token");
        }
        String out = workspace.grep("visible-token", ".");
        assertTrue(out.contains("keep.txt"));
        assertTrue(!out.contains(".venv/"));
        assertTrue(!out.contains("target/"));
        assertTrue(!out.contains("__pycache__/"));
        assertTrue(!out.contains("logs/"));
    }

    @Test
    void grepTruncatesAtMaxResults() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("hit-line-").append(i).append('\n');
        }
        Files.writeString(tempDir.resolve("many.txt"), sb.toString());
        String out = workspace.grep("hit-line-", ".", 5, 200, FileWorkspace.DEFAULT_SKIP_DIR_NAMES);
        long matchLines = out.lines().filter(ln -> ln.contains("hit-line-") && ln.contains(":")).count();
        assertEquals(5, matchLines);
        assertTrue(out.toLowerCase().contains("truncated"));
    }

    @Test
    void grepEmptyPatternError() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "x");
        String out = workspace.grep("", ".");
        assertTrue(out.toLowerCase().contains("error"));
    }
}
