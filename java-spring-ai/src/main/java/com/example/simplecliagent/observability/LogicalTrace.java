package com.example.simplecliagent.observability;

import com.example.simplecliagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 逻辑层轨迹：用 Logback 打印轮次 / tool 调用，对齐 Python {@code ConsoleTraceHandler}。
 *
 * <p>与 {@link HttpJsonlLogger} 分工：这里看 agent 循环；jsonl 看 HTTP wire。
 */
@Component
public class LogicalTrace {

    /** 专用 logger 名，便于 logback 按名称调级别。 */
    private static final Logger log = LoggerFactory.getLogger("cli.trace");

    private final AppProperties props;
    private int turn;

    public LogicalTrace(AppProperties props) {
        this.props = props;
    }

    /**
     * 用户新输入一轮对话时调用。
     */
    public void beginTurn() {
        if (!props.isVerbose()) {
            return;
        }
        turn++;
        // 直接 info：学习 demo 默认要看见
        log.info("──────── turn {} ────────", turn);
    }

    /**
     * 即将把用户消息交给 ChatClient。
     */
    public void userMessage(String text) {
        if (!props.isVerbose()) {
            return;
        }
        log.info(">>> user\n  {}", truncate(text));
    }

    /**
     * 模型最终自然语言回复（本轮 {@code ChatClient.call()} 返回后）。
     */
    public void assistantMessage(String text) {
        if (!props.isVerbose()) {
            return;
        }
        log.info("<<< assistant\n  {}", truncate(text));
    }

    /**
     * Tool 开始执行。
     */
    public void toolStart(String name, String argsSummary) {
        if (!props.isVerbose()) {
            return;
        }
        log.info(">>> tool {}\n  args: {}", name, truncate(argsSummary));
    }

    /**
     * Tool 执行结束。
     */
    public void toolEnd(String result) {
        if (!props.isVerbose()) {
            return;
        }
        String r = result == null ? "" : result;
        log.info("  result: ({} chars) {}", r.length(), truncate(r));
    }

    public void error(String message, Throwable t) {
        // 错误始终打，不受 verbose 关闭影响
        if (t != null) {
            log.error("error: {}", message, t);
        } else {
            log.error("error: {}", message);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int limit = props.getVerboseCharLimit();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...[" + text.length() + " chars total]";
    }
}
