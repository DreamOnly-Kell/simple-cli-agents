"""Tests for HTTP exchange logging and secret redaction."""

import json
from pathlib import Path

import httpx

from simple_cli_agent.http_logging import (
    HttpJsonlLogger,
    redact_headers,
    build_logging_http_client,
)


def test_redact_authorization_header() -> None:
    headers = {
        "Authorization": "Bearer sk-secret-key-123",
        "Content-Type": "application/json",
        "api-key": "also-secret",
    }
    redacted = redact_headers(headers)
    assert redacted["Authorization"] == "Bearer ***"
    assert redacted["api-key"] == "***"
    assert redacted["Content-Type"] == "application/json"
    assert "sk-secret" not in json.dumps(redacted)


def test_jsonl_writer_appends_valid_lines(tmp_path: Path) -> None:
    log_path = tmp_path / "session.jsonl"
    logger = HttpJsonlLogger(log_path)
    logger.write_exchange(
        request={
            "method": "POST",
            "url": "https://api.example.com/v1/chat/completions",
            "headers": {"Authorization": "Bearer sk-abc", "Content-Type": "application/json"},
            "body": {"model": "gpt-test", "messages": []},
        },
        response={
            "status_code": 200,
            "headers": {"content-type": "application/json"},
            "body": {"choices": []},
        },
    )
    lines = log_path.read_text(encoding="utf-8").strip().splitlines()
    assert len(lines) == 1
    record = json.loads(lines[0])
    assert record["request"]["headers"]["Authorization"] == "Bearer ***"
    assert "sk-abc" not in log_path.read_text(encoding="utf-8")
    assert record["response"]["status_code"] == 200
    assert record["request"]["body"]["model"] == "gpt-test"


def test_http_client_logs_request_response(tmp_path: Path) -> None:
    """Integration with httpx mock transport."""
    log_path = tmp_path / "http.jsonl"

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"ok": True, "echo": request.read().decode("utf-8")},
            request=request,
        )

    transport = httpx.MockTransport(handler)
    client = build_logging_http_client(log_path, transport=transport)
    resp = client.post(
        "https://api.example.com/v1/chat/completions",
        headers={"Authorization": "Bearer sk-xyz", "Content-Type": "application/json"},
        json={"model": "m", "messages": [{"role": "user", "content": "hi"}]},
    )
    assert resp.status_code == 200
    client.close()

    text = log_path.read_text(encoding="utf-8")
    assert "sk-xyz" not in text
    record = json.loads(text.strip().splitlines()[0])
    assert record["request"]["method"] == "POST"
    assert "chat/completions" in record["request"]["url"]
    assert record["request"]["body"]["model"] == "m"
    assert record["response"]["status_code"] == 200
    assert record["response"]["body"]["ok"] is True
