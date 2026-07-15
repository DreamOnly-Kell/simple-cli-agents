package com.example.simplecliagent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CommandGuard + ShellRunner 单测：不依赖 LLM，对齐 Python tests/test_shell_and_ls.py。
 */
class ShellToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void commandGuardBlocksDefaultDanger() {
        CommandGuard guard = new CommandGuard(CommandGuard.DEFAULT_BLOCKED_PATTERNS);
        String blocked = guard.check("sudo rm -rf /");
        assertNotNull(blocked);
        String lower = blocked.toLowerCase();
        assertTrue(lower.contains("blocked") || lower.contains("error"));

        String blocked2 = guard.check("mkfs.ext4 /dev/sda");
        assertNotNull(blocked2);
    }

    @Test
    void commandGuardAllowsSafeCommand() {
        CommandGuard guard = new CommandGuard(CommandGuard.DEFAULT_BLOCKED_PATTERNS);
        assertNull(guard.check("echo hello"));
        assertNull(guard.check("ls -la"));
        assertNull(guard.check("python --version"));
    }

    @Test
    void commandGuardUsesConfigPatterns() {
        CommandGuard guard = new CommandGuard(List.of("forbidden-word", "nope-cmd"));
        assertNull(guard.check("echo ok"));
        assertNotNull(guard.check("run forbidden-word here"));
        assertNotNull(guard.check("nope-cmd -x"));
    }

    @Test
    void commandGuardNormalizesWhitespaceAndCase() {
        CommandGuard guard = new CommandGuard(List.of("RM -RF /"));
        // multiple spaces + mixed case should still match after normalize
        String blocked = guard.check("  rm   -rf   /  ");
        assertNotNull(blocked);
        assertTrue(blocked.toLowerCase().contains("blocked"));
    }

    @Test
    void commandGuardEmptyCommand() {
        CommandGuard guard = new CommandGuard(CommandGuard.DEFAULT_BLOCKED_PATTERNS);
        assertNotNull(guard.check(""));
        assertNotNull(guard.check("   "));
        assertNotNull(guard.check(null));
    }

    @Test
    void shellRunnerExecutesSafeCommand() {
        ShellRunner runner = new ShellRunner(
                new FileWorkspace(tempDir),
                new CommandGuard(CommandGuard.DEFAULT_BLOCKED_PATTERNS),
                10);
        String out = runner.run("echo hello-agent");
        assertTrue(out.contains("hello-agent"));
        String lower = out.toLowerCase();
        assertTrue(out.contains("exit_code=0")
                || lower.contains("exit code: 0")
                || lower.contains("exit_code: 0"));
    }

    @Test
    void shellRunnerBlocksConfiguredDanger() {
        ShellRunner runner = new ShellRunner(
                new FileWorkspace(tempDir),
                new CommandGuard(List.of("rm -rf /", "sudo ")),
                5);
        String out = runner.run("sudo reboot");
        String lower = out.toLowerCase();
        assertTrue(lower.contains("blocked") || lower.contains("error"));
        // must not claim success as executed reboot alone without block
        assertTrue(!out.contains("exit_code=0") || lower.contains("blocked"));
    }

    @Test
    void shellRunnerRunsInWorkspaceRoot() {
        ShellRunner runner = new ShellRunner(
                new FileWorkspace(tempDir),
                new CommandGuard(List.of()),
                10);
        // pwd should be the temp workspace
        String out = runner.run("pwd");
        assertTrue(out.contains("exit_code=0"));
        assertTrue(out.contains(tempDir.toAbsolutePath().normalize().toString())
                || out.contains(tempDir.toString()));
    }
}
