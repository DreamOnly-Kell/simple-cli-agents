package com.example.simplecliagent;

import com.example.simplecliagent.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 入口。
 *
 * <p>本应用是无 Web 的 CLI agent：容器启动后由 {@code ReplRunner} 进入终端循环。
 * 对照 Python 版 {@code python -m simple_cli_agent}。
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SimpleCliAgentApplication {

    /**
     * 启动 Spring 容器；web-application-type=none 时不会拉起嵌入式 Tomcat。
     *
     * @param args 命令行参数（当前主要靠 application.yml / 环境变量配置）
     */
    public static void main(String[] args) {
        // 与 Python 版一致：失败时非 0 退出，便于脚本判断
        SpringApplication app = new SpringApplication(SimpleCliAgentApplication.class);
        // 双保险：即使依赖误引入 web starter，也尽量不启动 Web 服务器
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        app.run(args);
    }
}
