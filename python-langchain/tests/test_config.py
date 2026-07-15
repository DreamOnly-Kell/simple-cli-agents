"""Tests for configuration loading."""

import os
from pathlib import Path

from simple_cli_agent.config import AppConfig, load_config


def test_load_config_from_env(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("OPENAI_BASE_URL", "http://localhost:1234/v1")
    monkeypatch.setenv("OPENAI_MODEL", "local-model")
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path))
    monkeypatch.setenv("VERBOSE", "1")
    monkeypatch.setenv("HTTP_LOG", "1")
    monkeypatch.setenv("HTTP_LOG_DIR", str(tmp_path / "logs"))

    cfg = load_config()
    assert cfg.api_key == "sk-test"
    assert cfg.base_url == "http://localhost:1234/v1"
    assert cfg.model == "local-model"
    assert cfg.workspace_root == tmp_path.resolve()
    assert cfg.verbose is True
    assert cfg.http_log is True


def test_cli_overrides_env(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("OPENAI_API_KEY", "sk-env")
    monkeypatch.setenv("OPENAI_MODEL", "env-model")
    cfg = load_config(
        api_key=None,
        base_url="http://override/v1",
        model="cli-model",
        workspace=str(tmp_path),
        verbose=False,
        http_log=False,
    )
    assert cfg.base_url == "http://override/v1"
    assert cfg.model == "cli-model"
    assert cfg.verbose is False
    assert cfg.http_log is False
