"""Tests for workspace grep tool (shipped FileWorkspace / make_file_tools)."""

from pathlib import Path

from simple_cli_agent.tools.agent_tools import make_agent_tools
from simple_cli_agent.tools.files import FileWorkspace, make_file_tools
from simple_cli_agent.tools.shell import DEFAULT_BLOCKED_PATTERNS


def test_grep_finds_known_text_with_rel_path_and_line(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "app.py").write_text(
        "def main():\n    print('needle-xyz')\n",
        encoding="utf-8",
    )
    out = ws.grep("needle-xyz", ".")
    assert "src/app.py:2:" in out or "src\\app.py:2:" in out
    assert "needle-xyz" in out
    assert not out.lower().startswith("error")


def test_grep_no_matches(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    (tmp_path / "a.txt").write_text("hello", encoding="utf-8")
    out = ws.grep("not-present-zzz", ".")
    assert "no matches" in out.lower() or "0" in out.lower() or "not found" in out.lower()


def test_grep_path_escape_rejected(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    outside = tmp_path.parent / "outside_grep.txt"
    outside.write_text("secret-needle", encoding="utf-8")
    out = ws.grep("secret-needle", "../")
    assert "error" in out.lower() or "denied" in out.lower() or "escape" in out.lower()
    assert "outside_grep" not in out


def test_grep_skips_noise_dirs(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    (tmp_path / "keep.txt").write_text("visible-token", encoding="utf-8")
    for noise in (".venv", "target", "__pycache__", "logs"):
        d = tmp_path / noise
        d.mkdir()
        (d / "hidden.txt").write_text("visible-token", encoding="utf-8")

    out = ws.grep("visible-token", ".")
    assert "keep.txt" in out
    assert ".venv" not in out
    assert "target/" not in out and "target\\" not in out
    # path should not include noise dir segment
    for noise in (".venv", "target", "__pycache__", "logs"):
        assert f"{noise}/" not in out and f"{noise}\\" not in out


def test_grep_truncates_at_max_results(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    lines = [f"hit-line-{i}" for i in range(20)]
    (tmp_path / "many.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    out = ws.grep("hit-line-", ".", max_results=5)
    # exactly 5 match lines + truncation note
    match_lines = [ln for ln in out.splitlines() if "hit-line-" in ln and ":" in ln]
    assert len(match_lines) == 5
    assert "truncated" in out.lower()


def test_grep_empty_pattern_error(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    (tmp_path / "a.txt").write_text("x", encoding="utf-8")
    out = ws.grep("", ".")
    assert "error" in out.lower()


def test_grep_tool_registered(tmp_path: Path) -> None:
    tools = {t.name: t for t in make_file_tools(FileWorkspace(tmp_path))}
    assert "grep" in tools
    (tmp_path / "t.txt").write_text("alpha-beta", encoding="utf-8")
    out = tools["grep"].invoke({"pattern": "alpha-beta", "path": "."})
    assert "t.txt" in out
    assert "alpha-beta" in out


def test_agent_tools_include_grep(tmp_path: Path) -> None:
    tools = {
        t.name: t
        for t in make_agent_tools(
            FileWorkspace(tmp_path),
            blocked_patterns=DEFAULT_BLOCKED_PATTERNS,
            shell_timeout_seconds=5,
        )
    }
    assert "grep" in tools
