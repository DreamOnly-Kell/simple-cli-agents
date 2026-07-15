"""Tests for ls + run_command tools and dangerous-command policy."""

from pathlib import Path

from simple_cli_agent.tools.files import FileWorkspace
from simple_cli_agent.tools.shell import CommandGuard, ShellRunner, DEFAULT_BLOCKED_PATTERNS
from simple_cli_agent.tools.agent_tools import make_agent_tools


def test_ls_lists_workspace_files(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    (tmp_path / "a.txt").write_text("x", encoding="utf-8")
    (tmp_path / "sub").mkdir()
    (tmp_path / "sub" / "b.txt").write_text("y", encoding="utf-8")

    out = ws.list_dir(".")
    assert "a.txt" in out
    assert "sub/" in out or "sub" in out

    out_sub = ws.list_dir("sub")
    assert "b.txt" in out_sub


def test_ls_rejects_escape(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    result = ws.list_dir("../")
    assert "error" in result.lower() or "denied" in result.lower() or "escape" in result.lower()


def test_command_guard_blocks_default_danger() -> None:
    guard = CommandGuard(DEFAULT_BLOCKED_PATTERNS)
    blocked = guard.check("sudo rm -rf /")
    assert blocked is not None
    assert "blocked" in blocked.lower() or "error" in blocked.lower()

    blocked2 = guard.check("mkfs.ext4 /dev/sda")
    assert blocked2 is not None


def test_command_guard_allows_safe_command() -> None:
    guard = CommandGuard(DEFAULT_BLOCKED_PATTERNS)
    assert guard.check("echo hello") is None
    assert guard.check("ls -la") is None
    assert guard.check("python --version") is None


def test_command_guard_uses_config_patterns() -> None:
    guard = CommandGuard(("forbidden-word", "nope-cmd"))
    assert guard.check("echo ok") is None
    assert guard.check("run forbidden-word here") is not None
    assert guard.check("nope-cmd -x") is not None


def test_shell_runner_executes_safe_command(tmp_path: Path) -> None:
    runner = ShellRunner(
        workspace=FileWorkspace(tmp_path),
        guard=CommandGuard(DEFAULT_BLOCKED_PATTERNS),
        timeout_seconds=10,
    )
    out = runner.run("echo hello-agent")
    assert "hello-agent" in out
    assert "exit_code=0" in out or "exit code: 0" in out.lower() or "exit_code: 0" in out


def test_shell_runner_blocks_configured_danger(tmp_path: Path) -> None:
    runner = ShellRunner(
        workspace=FileWorkspace(tmp_path),
        guard=CommandGuard(("rm -rf /", "sudo ")),
        timeout_seconds=5,
    )
    out = runner.run("sudo reboot")
    assert "blocked" in out.lower() or "error" in out.lower()
    # must not claim success as executed reboot
    assert "exit_code=0" not in out or "blocked" in out.lower()


def test_agent_tools_include_ls_and_run_command(tmp_path: Path) -> None:
    tools = {t.name: t for t in make_agent_tools(
        workspace=FileWorkspace(tmp_path),
        blocked_patterns=DEFAULT_BLOCKED_PATTERNS,
        shell_timeout_seconds=10,
    )}
    assert "ls" in tools
    assert "run_command" in tools
    assert "read_file" in tools
    assert "write_file" in tools

    (tmp_path / "listed.txt").write_text("ok", encoding="utf-8")
    listing = tools["ls"].invoke({"path": "."})
    assert "listed.txt" in listing

    run_out = tools["run_command"].invoke({"command": "echo from-tool"})
    assert "from-tool" in run_out

    blocked = tools["run_command"].invoke({"command": "rm -rf /"})
    assert "error" in blocked.lower() or "blocked" in blocked.lower()
