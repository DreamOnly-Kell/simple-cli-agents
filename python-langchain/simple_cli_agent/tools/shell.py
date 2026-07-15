"""
终端命令执行与高危命令策略。

学习要点：
- shell tool 能力很强，必须用可配置策略拦高危命令。
- 策略默认是「子串匹配」（规范化空白后大小写不敏感），便于配置与理解。
- 命令在 workspace 根目录下执行（cwd=root），输出截断避免撑爆上下文。
- 被拦截时不启动进程，直接把 Error 字符串回给模型，便于它改写命令。
"""

from __future__ import annotations

import subprocess

from simple_cli_agent.tools.files import FileWorkspace

# 默认高危子串（大小写不敏感、空白压缩后做 contains）。
# 刻意用子串而非 AST：好配、好讲；挡不住所有变形，也不是 OS 沙箱。
DEFAULT_BLOCKED_PATTERNS: tuple[str, ...] = (
    "rm -rf /",
    "rm -rf/*",
    "rm -rf ~",
    "mkfs",
    "dd if=",
    ":(){",  # fork bomb 常见片段
    "shutdown",
    "reboot",
    "halt",
    "poweroff",
    "curl|sh",  # 无空格管道写法
    "curl | sh",
    "wget|sh",
    "wget | sh",
    "sudo ",  # 尾空格：降低误伤含 sudo 字样的无害 token
    "chmod -r 777 /",
    "> /dev/sd",
)


class CommandGuard:
    """
    根据配置的 pattern 列表拦截危险命令。

    pattern 匹配规则：
        将 command 与 pattern 都压缩空白并 lower 后，若 pattern 为 command 的子串则拦截。

    为何不用完整 shell AST：
        学习 demo 优先可配置、可解释；子串策略足够挡住常见误用与提示注入样例。
    """

    def __init__(self, patterns: tuple[str, ...] | list[str]) -> None:
        # 丢掉空项：normalize 后空串是任意字符串的子串，会拦死一切命令
        self.patterns = tuple(p for p in patterns if p and str(p).strip())

    @staticmethod
    def _normalize(text: str) -> str:
        # split 无参：任意空白切分再单空格拼回，并 lower
        return " ".join(text.split()).lower()

    def check(self, command: str) -> str | None:
        """
        返回 None=放行；返回 str=拦截原因（Error:…，可直接当 tool 结果）。
        """
        if not command or not command.strip():
            return "Error: empty command"
        normalized = self._normalize(command)
        for pattern in self.patterns:
            p = self._normalize(pattern)
            # 用规范化后的子串匹配；回传仍带「原始 pattern / 原始 command」便于对照配置
            if p and p in normalized:
                return (
                    f"Error: blocked dangerous command "
                    f"(matched policy '{pattern}'): {command}"
                )
        return None


class ShellRunner:
    """
    在工作区根目录执行 shell 命令；先过 CommandGuard，再 subprocess。

    与 FileWorkspace 的关系：
        只复用 workspace.root 作为 cwd；命令本身仍可访问 cwd 外路径
        （应用层 path jail 管文件 tool，不管 shell 的任意路径——这是 v1 的明确取舍）。
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
        # timeout=0 在 subprocess 里会立刻 TimeoutExpired，故下限 1s
        self.timeout_seconds = max(1, int(timeout_seconds))
        self.max_output_chars = max_output_chars

    def run(self, command: str) -> str:
        """
        先 Guard；放行后 subprocess。返回固定文本：exit_code + stdout/stderr。
        拦截时不 spawn 子进程。
        """
        blocked = self.guard.check(command)
        if blocked is not None:
            return blocked

        try:
            # shell=True：模型给的是整行 shell 字符串，不是 argv 列表
            # cwd 钉死 workspace.root（与文件 tool 根一致），与进程启动时 cwd 无关
            completed = subprocess.run(
                command,
                shell=True,
                cwd=str(self.workspace.root),
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
            )
        except subprocess.TimeoutExpired:
            # 超时后不附带部分输出，避免半截结果误导模型
            return (
                f"Error: command timed out after {self.timeout_seconds}s: {command}"
            )
        except OSError as exc:
            return f"Error: failed to run command: {exc}"

        stdout = completed.stdout or ""
        stderr = completed.stderr or ""
        # 非 0 也当正常 tool 结果返回（不是 Python 异常），由模型读 exit_code 再决策
        combined = (
            f"exit_code={completed.returncode}\n"
            f"--- stdout ---\n{stdout}"
            f"--- stderr ---\n{stderr}"
        )
        if len(combined) > self.max_output_chars:
            # 先截断再拼提示；提示里 total 用截断前长度
            combined = (
                combined[: self.max_output_chars]
                + f"\n...[truncated, total {len(combined)} chars]"
            )
        return combined
