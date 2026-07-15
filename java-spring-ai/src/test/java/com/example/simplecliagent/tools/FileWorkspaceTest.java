package com.example.simplecliagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
