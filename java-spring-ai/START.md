# 启动速查（Maven）

> **构建请用 Maven**。  
> **配置只走 Spring**：`application.yml` / `application-local.yml` / Spring 环境变量 / 启动参数。  
> **不读取** 项目根目录 `.env` 文件。

## 前置

```bash
java -version    # 建议 21
mvn -version
```

## 配置（api-key 与 base-url 不要写反）

| 属性 | 应是什么 | 示例 |
|------|----------|------|
| `spring.ai.openai.api-key` | 密钥字符串 | `sk-...` / `tp-...` |
| `spring.ai.openai.base-url` | **必须** `http(s)://` 的 URL，且**不要**带 `/v1` | `https://token-plan-cn.xiaomimimo.com` |

推荐用 local profile：

```bash
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml
# 编辑 application-local.yml
```

```yaml
spring:
  ai:
    openai:
      api-key: tp-你的密钥
      base-url: https://token-plan-cn.xiaomimimo.com
      chat:
        options:
          model: mimo-v2.5
```

## 启动

```bash
cd /path/to/Simple-cli-agent-java
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

也可用 **Spring 完整属性名** 环境变量（避免和 Python 的 `OPENAI_*` 混用搞混）：

```bash
export SPRING_AI_OPENAI_API_KEY=tp-...
export SPRING_AI_OPENAI_BASE_URL=https://token-plan-cn.xiaomimimo.com
export SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=mimo-v2.5
mvn spring-boot:run
```

启动后看 **connection diagnostics**：
- `base-url` 应以 `https://` 开头  
- `api-key` 不应像 URL；若写反，启动会直接报错提示 swapped  

## 测试

```bash
mvn test
```

## 日志双开

| 通道 | 位置 |
|------|------|
| Logback | 控制台 + `logs/app.log` |
| HTTP JSONL | `logs/http-session-*.jsonl` |

## 姊妹项目

Python 版：[`../python-langchain`](../python-langchain/)（可用 `.env`；本子项目只用 Spring 配置）。
