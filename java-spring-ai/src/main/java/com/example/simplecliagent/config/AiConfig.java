package com.example.simplecliagent.config;

import com.example.simplecliagent.observability.HttpJsonlLogger;
import com.example.simplecliagent.observability.HttpLoggingInterceptor;
import com.example.simplecliagent.tools.FileTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Spring AI 装配：ChatMemory、ChatClient（系统提示 + 文件 tools + 多轮记忆）。
 *
 * <p>对照 Python {@code build_agent} / {@code build_chat_model}。
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * 系统提示：角色、工具说明、turn-based 行为约定。
     * 与 Python SYSTEM_PROMPT 同意图。
     */
    public static final String SYSTEM_PROMPT = """
            You are a minimal terminal coding assistant for learning agent/tool-use.

            You have two tools:
            - readFile(path): read a text file under the workspace
            - writeFile(path, content): create or overwrite a text file under the workspace

            Rules:
            - Prefer tools when the user asks about or wants to change files.
            - Paths are relative to the workspace root.
            - Be concise. After finishing the user's request for this turn, stop and wait
              (do not invent extra tasks).
            """;

    /**
     * 进程内对话记忆：窗口限制防止无限涨上下文。
     *
     * <p>对照 Python {@code MemorySaver}（进程内、退出即丢）。
     */
    @Bean
    public ChatMemory chatMemory() {
        // 保留最近若干条消息，避免长会话无限膨胀
        return MessageWindowChatMemory.builder()
                .maxMessages(50)
                .build();
    }

    /**
     * 组装 ChatClient：系统提示 + FileTools + Memory Advisor。
     *
     * <p>Tool calling 循环由 Spring AI 在 {@code call()} 内处理，
     * 对应 Python {@code create_agent} 内部 model↔tool 多跳。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, FileTools fileTools, ChatMemory chatMemory) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                // 注册 @Tool 方法所在 bean；模型可见 tools schema
                .defaultTools(fileTools)
                // 多轮：同一 conversationId 下自动带历史
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * HTTP JSONL 写入器：每个进程一个 session 文件。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app", name = "http-log", havingValue = "true", matchIfMissing = true)
    public HttpJsonlLogger httpJsonlLogger(AppProperties props) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        var path = props.resolvedHttpLogDir().resolve("http-session-" + stamp + ".jsonl");
        log.info("[http-log] writing exchanges to {}", path);
        return new HttpJsonlLogger(path);
    }

    /**
     * 把拦截器挂到 Spring 使用的 RestClient 上，从而截获 OpenAI 兼容 HTTP。
     *
     * <p>若某版本 Spring AI 不用 RestClient，此 Bean 无害；wire 日志可能为空，逻辑轨迹仍可用。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app", name = "http-log", havingValue = "true", matchIfMissing = true)
    public RestClientCustomizer httpLoggingRestClientCustomizer(ObjectProvider<HttpJsonlLogger> loggerProvider) {
        return restClientBuilder -> {
            HttpJsonlLogger logger = loggerProvider.getIfAvailable();
            if (logger != null) {
                restClientBuilder.requestInterceptor(new HttpLoggingInterceptor(logger));
            }
        };
    }
}
