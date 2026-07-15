"""Tests for edit_file unique-match local edit (shipped FileWorkspace / tools)."""

from pathlib import Path

from simple_cli_agent.tools.agent_tools import make_agent_tools
from simple_cli_agent.tools.files import FileWorkspace, make_file_tools
from simple_cli_agent.tools.shell import DEFAULT_BLOCKED_PATTERNS


def test_edit_file_unique_match_succeeds(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    target = tmp_path / "sample.py"
    target.write_text("def hello():\n    return 1\n", encoding="utf-8")

    msg = ws.edit_file("sample.py", "return 1", "return 2")
    assert "error" not in msg.lower()
    assert "1" in msg or "edited" in msg.lower() or "replaced" in msg.lower()
    assert target.read_text(encoding="utf-8") == "def hello():\n    return 2\n"
    assert ws.read_file("sample.py") == "def hello():\n    return 2\n"


def test_edit_file_zero_matches_unchanged(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    target = tmp_path / "a.txt"
    original = "alpha beta gamma"
    target.write_text(original, encoding="utf-8")

    msg = ws.edit_file("a.txt", "missing-token", "x")
    assert "error" in msg.lower()
    assert "0" in msg or "not found" in msg.lower()
    assert target.read_text(encoding="utf-8") == original


def test_edit_file_multiple_matches_unchanged(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    target = tmp_path / "b.txt"
    original = "xx foo yy foo zz"
    target.write_text(original, encoding="utf-8")

    msg = ws.edit_file("b.txt", "foo", "bar")
    assert "error" in msg.lower()
    assert "2" in msg or "multiple" in msg.lower() or "times" in msg.lower()
    assert target.read_text(encoding="utf-8") == original


def test_edit_file_path_escape_rejected(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    outside = tmp_path.parent / "outside_edit.txt"
    outside.write_text("secret", encoding="utf-8")

    msg = ws.edit_file("../outside_edit.txt", "secret", "hacked")
    assert "error" in msg.lower() or "denied" in msg.lower() or "escape" in msg.lower()
    assert outside.read_text(encoding="utf-8") == "secret"


def test_edit_file_empty_old_str_rejected(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    target = tmp_path / "c.txt"
    target.write_text("keep", encoding="utf-8")
    msg = ws.edit_file("c.txt", "", "x")
    assert "error" in msg.lower()
    assert target.read_text(encoding="utf-8") == "keep"


def test_edit_file_tool_registered_and_invokable(tmp_path: Path) -> None:
    tools = {t.name: t for t in make_file_tools(FileWorkspace(tmp_path))}
    assert "edit_file" in tools
    (tmp_path / "t.txt").write_text("one two three", encoding="utf-8")
    out = tools["edit_file"].invoke(
        {"path": "t.txt", "old_str": "two", "new_str": "2"}
    )
    assert "error" not in out.lower()
    assert (tmp_path / "t.txt").read_text(encoding="utf-8") == "one 2 three"


def test_agent_tools_include_edit_file(tmp_path: Path) -> None:
    tools = {
        t.name: t
        for t in make_agent_tools(
            FileWorkspace(tmp_path),
            blocked_patterns=DEFAULT_BLOCKED_PATTERNS,
            shell_timeout_seconds=5,
        )
    }
    assert "edit_file" in tools
    (tmp_path / "z.txt").write_text("aaa", encoding="utf-8")
    tools["edit_file"].invoke({"path": "z.txt", "old_str": "aaa", "new_str": "bbb"})
    assert (tmp_path / "z.txt").read_text(encoding="utf-8") == "bbb"
