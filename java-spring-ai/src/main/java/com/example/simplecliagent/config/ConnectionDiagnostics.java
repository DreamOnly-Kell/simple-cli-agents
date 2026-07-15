package com.example.simplecliagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动时打印 OpenAI 连接诊断，并校验 api-key / base-url 是否写反。
 */
@Component
@Order(10)
public class ConnectionDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConnectionDiagnostics.class);

    private final Environment env;
    private final OpenAiConnectionProperties connectionProperties;
    private final AppProperties appProperties;

    public ConnectionDiagnostics(
            Environment env,
            OpenAiConnectionProperties connectionProperties,
            AppProperties appProperties) {
        this.env = env;
        this.connectionProperties = connectionProperties;
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String base = connectionProperties.getBaseUrl();
        String key = connectionProperties.getApiKey();
        String model = env.getProperty("spring.ai.openai.chat.options.model", "?");

        System.out.println("----- connection diagnostics -----");
        System.out.println("workspace: " + appProperties.resolvedWorkspaceRoot());
        System.out.println("spring.ai.openai.base-url: " + base);
        System.out.println("expected chat URL:         "
                + (base == null ? "?" : base.replaceAll("/+$", "") + "/v1/chat/completions"));
        System.out.println("model: " + model);
        System.out.println("api-key set: " + describeKey(key));
        System.out.println("----------------------------------");

        validate(base, key);
        log.info("OpenAI connection ok: baseUrl={}, model={}", base, model);
    }

    /**
     * 校验 base-url / api-key 形态，防止两者写反。
     */
    static void validate(String base, String key) {
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                    "spring.ai.openai.base-url is empty. "
                            + "Set it in application.yml / application-local.yml to an https URL.");
        }
        // base-url 看起来像密钥（没有 scheme，或 tp-/sk- 开头）
        if (!looksLikeHttpUrl(base)) {
            throw new IllegalStateException(
                    "spring.ai.openai.base-url looks invalid (got '" + mask(base) + "'). "
                            + "It must be an absolute URL like https://api.openai.com — "
                            + "if this looks like your API key, you swapped api-key and base-url.");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "spring.ai.openai.api-key is empty. "
                            + "Set it in application-local.yml or SPRING_AI_OPENAI_API_KEY.");
        }
        // api-key 看起来像 URL → 写反了
        if (looksLikeHttpUrl(key)) {
            throw new IllegalStateException(
                    "spring.ai.openai.api-key looks like a URL ('" + mask(key) + "'). "
                            + "You probably swapped api-key and base-url in the config.");
        }
    }

    static boolean looksLikeHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://");
    }

    private static String describeKey(String key) {
        if (key == null || key.isBlank()) {
            return "false ← 请在 application.yml / application-local.yml 配置 api-key";
        }
        if (looksLikeHttpUrl(key)) {
            return "true but looks like URL! (swapped?) len=" + key.length();
        }
        return "true (len=" + key.length() + ", prefix=" + mask(key) + ")";
    }

    /** 只展示前后几位，避免把完整密钥打到控制台。 */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        String v = value.trim();
        if (v.length() <= 8) {
            return v.charAt(0) + "***";
        }
        return v.substring(0, 4) + "..." + v.substring(v.length() - 4);
    }
}
