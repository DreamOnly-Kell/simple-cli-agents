"""Tests for workspace-sandboxed read/write tools."""

from pathlib import Path

import pytest

from simple_cli_agent.tools.files import FileWorkspace, make_file_tools


def test_write_and_read_round_trip(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    tools = {t.name: t for t in make_file_tools(ws)}
    msg = tools["write_file"].invoke({"path": "hello.txt", "content": "hi there"})
    assert "hello.txt" in msg
    assert (tmp_path / "hello.txt").read_text(encoding="utf-8") == "hi there"
    body = tools["read_file"].invoke({"path": "hello.txt"})
    assert body == "hi there"


def test_rejects_path_escape(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    tools = {t.name: t for t in make_file_tools(ws)}
    outside = tmp_path.parent / "secret.txt"
    outside.write_text("nope", encoding="utf-8")
    result = tools["read_file"].invoke({"path": "../secret.txt"})
    assert "error" in result.lower() or "denied" in result.lower() or "escape" in result.lower()
    assert "nope" not in result


def test_write_creates_parent_dirs(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    tools = {t.name: t for t in make_file_tools(ws)}
    tools["write_file"].invoke({"path": "a/b/c.txt", "content": "nested"})
    assert (tmp_path / "a" / "b" / "c.txt").read_text(encoding="utf-8") == "nested"


def test_read_missing_file_returns_error(tmp_path: Path) -> None:
    ws = FileWorkspace(tmp_path)
    tools = {t.name: t for t in make_file_tools(ws)}
    result = tools["read_file"].invoke({"path": "missing.txt"})
    assert "error" in result.lower() or "not found" in result.lower()
