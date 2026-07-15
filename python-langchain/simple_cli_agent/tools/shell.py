"""
终端命令执行与高危命令策略。

学习要点：
- shell tool 能力很强，必须用可配置策略拦高危命令。
- 策略默认是「子串匹配」（规范化空白后大小写不敏感），便于配置与理解。
- 命令在 workspace 根目录下执行（cwd=root），输出截断避免撑爆上下文。
"""

from __future__ import annotations

import subprocess
from pathlib import Path

from simple_cli_agent.tools.files import FileWorkspace

# 默认高危模式：可通过配置覆盖；故意用易配置的子串而非复杂正则
DEFAULT_BLOCKED_PATTERNS: tuple[str, ...] = (
    "rm -rf /",
    "rm -rf/*",
    "rm -rf ~",
    "mkfs",
    "dd if=",
    ":(){",
    "shutdown",
    "reboot",
    "halt",
    "poweroff",
    "curl|sh",
    "curl | sh",
    "wget|sh",
    "wget | sh",
    "sudo ",
    "chmod -r 777 /",
    "> /dev/sd",
)


class CommandGuard:
    """
    根据配置的 pattern 列表拦截危险命令。

    pattern 匹配规则：
        将 command 与 pattern 都压缩空白并 lower 后，若 pattern 为 command 的子串则拦截。
    """

    def __init__(self, patterns: tuple[str, ...] | list[str]) -> None:
        self.patterns = tuple(p for p in patterns if p and str(p).strip())

    @staticmethod
    def _normalize(text: str) -> str:
        return " ".join(text.split()).lower()

    def check(self, command: str) -> str | None:
        """
        检查命令是否被策略拦截。

        返回:
            None — 允许执行
            str  — 拦截原因（Error: 开头），供 tool 直接返回给模型
        """
        if not command or not command.strip():
            return "Error: empty command"
        normalized = self._normalize(command)
        for pattern in self.patterns:
            p = self._normalize(pattern)
            if p and p in normalized:
                return (
                    f"Error: blocked dangerous command "
                    f"(matched policy '{pattern}'): {command}"
                )
        return None


class ShellRunner:
    """
    在工作区根目录执行 shell 命令；先过 CommandGuard，再 subprocess。
    """

    def __init__(
        self,
        workspace: FileWorkspace,
        guard: CommandGuard,
        timeout_seconds: int = 30,
        max_output_chars: int = 50_000,
    ) -> None:
        self.workspace = workspace
        self.guard = guard
        self.timeout_seconds = max(1, int(timeout_seconds))
        self.max_output_chars = max_output_chars

    def run(self, command: str) -> str:
        """
        执行命令并返回可读结果字符串（含 exit code / stdout / stderr）。

        被策略拦截时不启动进程。
        """
        blocked = self.guard.check(command)
        if blocked is not None:
            return blocked

        try:
            completed = subprocess.run(
                command,
                shell=True,
                cwd=str(self.workspace.root),
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
            )
        except subprocess.TimeoutExpired:
            return (
                f"Error: command timed out after {self.timeout_seconds}s: {command}"
            )
        except OSError as exc:
            return f"Error: failed to run command: {exc}"

        stdout = completed.stdout or ""
        stderr = completed.stderr or ""
        combined = (
            f"exit_code={completed.returncode}\n"
            f"--- stdout ---\n{stdout}"
            f"--- stderr ---\n{stderr}"
        )
        if len(combined) > self.max_output_chars:
            combined = (
                combined[: self.max_output_chars]
                + f"\n...[truncated, total {len(combined)} chars]"
            )
        return combined
