package com.example.simplecliagent.cli;

import com.example.simplecliagent.config.AppProperties;
import com.example.simplecliagent.observability.LogicalTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 终端 REPL：外层人机循环；每轮 {@code ChatClient.call()} 结束后等人。
 *
 * <p>两层循环（对照 DNA-05 / Python {@code cli.main}）：
 * <ul>
 *   <li>外层：readLine → call → print → readLine</li>
 *   <li>内层：单次 call 内 model ⇄ tools 多跳（Spring AI 执行）</li>
 * </ul>
 *
 * <p>{@link Order} 取较大值，确保 ConnectionDiagnostics 等先跑完。
 */
@Component
@Order(100)
public class ReplRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReplRunner.class);

    private final ChatClient chatClient;
    private final AppProperties props;
    private final LogicalTrace trace;

    public ReplRunner(ChatClient chatClient, AppProperties props, LogicalTrace trace) {
        this.chatClient = chatClient;
        this.props = props;
        this.trace = trace;
    }

    /**
     * 启动后进入阻塞式 REPL，直到 exit/quit/EOF。
     */
    @Override
    public void run(ApplicationArguments args) {
        // 同一会话共用 conversationId，MessageChatMemoryAdvisor 靠它串历史
        String conversationId = UUID.randomUUID().toString();

        // 连接详情已由 ConnectionDiagnostics 打印
        System.out.println("commands: exit | quit | Ctrl-C/D  —  type a message to chat");
        System.out.println("conversationId: " + conversationId);
        System.out.println();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.print("you> ");
                System.out.flush();
                String line;
                try {
                    line = reader.readLine();
                } catch (Exception e) {
                    System.out.println("\nbye");
                    return;
                }
                if (line == null) {
                    // EOF
                    System.out.println("\nbye");
                    return;
                }
                String userText = line.strip();
                if (userText.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(userText) || "quit".equalsIgnoreCase(userText)) {
                    System.out.println("bye");
                    return;
                }

                trace.beginTurn();
                trace.userMessage(userText);

                try {
                    // 单次 call：内部可能多跳 tool；返回即本轮结束（DNA-05）
                    String answer = chatClient.prompt()
                            .user(userText)
                            // 1.1.x：通过 advisor 参数绑定会话 id
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                            .call()
                            .content();

                    if (answer == null || answer.isBlank()) {
                        answer = "(no text content)";
                    }
                    trace.assistantMessage(answer);
                    System.out.println("assistant> " + answer);
                } catch (Exception ex) {
                    // 单轮失败不退出 REPL，便于学习排查
                    trace.error(ex.getMessage(), ex);
                    System.err.println("error: " + ex.getMessage());
                    log.debug("turn failed", ex);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("REPL failed", e);
        }
    }
}
