# JasonCode（一期工程）Plan

> 依据：已批准的 `docs/phase-1/spec.md`（F1~F8、N1~N8）。

## 架构概览

整体分为 6 个模块，依赖方向严格单向：`cli → chat → {provider, ui, history, config}`，provider 与 ui 互不感知。

### cli（启动编排）
程序入口。解析命令行参数（`-p/--provider`、`--config`、`--help/--version`），加载配置、选定供应商、组装各模块，启动对话循环。

### config（配置系统）
读取并校验 `~/.jasoncode/config.yaml`（或 `--config` 指定路径）。YAML → 强类型模型；字段缺失、协议非法、default 不存在等错误返回结构化的错误描述。API key 的字符串表示在此层做掩码（对外只显示末四位）。对应 F1、N3。

### provider（协议适配层）——核心抽象
定义统一的对话请求接口与归一化的流式事件；两个实现分别对接 OpenAI 协议与 Anthropic 协议（含 SSE 解析、请求/响应 JSON 编解码、thinking 处理、错误结构翻译）。工厂根据 `protocol` 字段创建实例。对应 F4、F5、F7、AC7。

### history（历史存储层）——预留持久化的抽象
定义对话历史的存取接口，一期提供内存实现（按序追加、快照读取）。对话循环只依赖接口，二期落盘时新增实现即可。对应 F6、N2。

### chat（对话循环）
会话编排。每轮：读取输入 → 追加用户消息 → 携带完整历史调用 provider → 流式事件交给 UI 渲染 → 完成后追加 assistant 消息。统一处理每轮错误：打印可读信息、回滚用户消息、本轮结束、循环继续。对应 F6、F8。

### ui（终端交互与渲染）
- 输入侧：JLine LineReader 提供提示符、输入历史（↑/↓）、管道环境检测（无颜色降级）；启动 LOGO 横幅与供应商信息；可扩展的会话内命令系统（一期 `/exit`、`/help`）。
- 输出侧：事件队列 + 独立渲染线程消费流式事件，thinking 用暗色区分、正文正常输出；负责退出时的终端状态恢复。对应 F3、F4、F5、N1、N4、N7、N8。

## 核心数据结构

### config 模块

```java
// ~/.jasoncode/config.yaml 的强类型映射
public record JasonConfig(
    @JsonProperty("default") String defaultProvider,
    List<ProviderConfig> providers
) {}

public record ProviderConfig(
    String name,          // 供应商名，如 kimi / deepseek / claude
    Protocol protocol,    // OPENAI | ANTHROPIC，非法值解析即报错
    String model,
    @JsonProperty("base_url") String baseUrl,
    @JsonProperty("api_key") String apiKey,
    boolean thinking      // 可选，默认 false
) {
    @Override public String toString();  // apiKey 掩码为 ****末四位
}

public enum Protocol { OPENAI, ANTHROPIC }

public final class ConfigLoader {
    public static JasonConfig load(Path path);  // 抛 ConfigException（携带人类可读原因）
}
```

### provider 模块（核心抽象）

```java
public enum Role { USER, ASSISTANT }

public record ChatMessage(Role role, String content) {}

// 归一化流式事件：上层只认这个，不认协议细节
public sealed interface StreamEvent {
    record ThinkingDelta(String text) implements StreamEvent {}
    record TextDelta(String text) implements StreamEvent {}
    record Done() implements StreamEvent {}
    record Error(ProviderException error) implements StreamEvent {}
}

// 事件监听器：ui 侧实现它，把事件投入渲染队列
public interface StreamEventListener {
    void onEvent(StreamEvent event);
}

// 统一 Provider 接口（F7）
public interface ChatProvider {
    /** 发起流式对话；返回的 Future 在流结束（成功或失败）时完成 */
    CompletableFuture<Void> streamChat(List<ChatMessage> history, StreamEventListener listener);
    String describe();  // 如 "claude (anthropic / claude-sonnet-4-5)"，用于启动横幅
}

public final class ProviderFactory {
    public static ChatProvider create(ProviderConfig config);  // 按 protocol 分发
}

public final class ProviderException extends RuntimeException {
    // 分类：NETWORK / AUTH / API / PARSE；message 人类可读，绝不包含完整 apiKey
}
```

### history 模块

```java
public interface HistoryStore {
    void append(ChatMessage message);
    List<ChatMessage> snapshot();  // 不可变副本，供请求构造
    void removeLast();             // 请求失败时回滚用户消息
    void clear();
}

public final class InMemoryHistoryStore implements HistoryStore {}  // 一期唯一实现
```

### ui 模块

```java
// 渲染器：内部持有阻塞队列 + 渲染线程，对外只暴露事件入口
public final class StreamRenderer implements StreamEventListener {
    public void start();                          // 启动渲染线程
    @Override public void onEvent(StreamEvent event);  // 入队，立即返回（不阻塞网络线程）
    public void awaitDrain();                     // 等待当前流打印完毕
    public String takeTurnText();                 // 取出本轮累积的 assistant 正文（供 history 追加）
    public void close();                          // 停止线程、恢复终端状态
}

// 终端门面：LOGO、提示符、输入读取、错误/警告输出
public final class ConsoleUi {
    public void printBanner(ChatProvider provider);   // LOGO + 供应商/模型信息
    public String readLine();                          // JLine 读取，null = EOF（Ctrl+D）
    public void showError(String message);
    public void showWarning(String message);
}

// 命令系统：可扩展注册（为后续 /sessions 等预留）
public interface ChatCommand {
    String name();                    // 如 "exit"
    String description();             // /help 展示用
    CommandResult execute(ChatContext ctx, String args);
}
public enum CommandResult { CONTINUE, EXIT }
```

### chat 模块

```java
// 单个会话对象：历史 + 元信息（为后续会话切换预留的边界）
public final class Conversation {
    public Conversation(HistoryStore history);
    public HistoryStore history();
}

public final class ConversationLoop {
    public ConversationLoop(ConsoleUi ui, ChatProvider provider, Conversation conversation);
    public void run();  // 主循环：命令分发 / 调用 provider / 错误兜底，直到退出
}
```

## 模块设计

### cli / Main
**职责：** picocli 命令定义、启动序列编排、退出清理。
**对外接口：** `main(String[] args)`。
**依赖：** config、provider、chat、ui。

### config
**职责：** YAML 加载、字段校验（name/protocol/model/base_url/api_key 非空，protocol 枚举合法，default 存在于 providers，name 不重复）、掩码输出。
**对外接口：** `ConfigLoader.load(Path)`。
**依赖：** 无（Jackson YAML）。

### provider
**职责：** 统一接口 + 两个协议实现 + 通用 SSE 行解析器。
**对外接口：** `ChatProvider`、`ProviderFactory`、`StreamEvent`、`StreamEventListener`。
**依赖：** config 的 `ProviderConfig`。

### history
**职责：** 历史存取抽象与内存实现。
**对外接口：** `HistoryStore`。
**依赖：** provider 的消息模型。

### chat
**职责：** 会话循环编排、命令分发、每轮错误兜底（打印错误 + 回滚 user 消息）。
**对外接口：** `ConversationLoop.run()`。
**依赖：** provider 接口、history 接口、ui 接口。

### ui
**职责：** 输入（JLine）、命令输出、流式渲染（队列 + 渲染线程）、LOGO 与颜色管理、终端状态恢复。
**对外接口：** `ConsoleUi`、`StreamRenderer`、`ChatCommand` 注册。
**依赖：** provider 的事件模型。

## 模块交互

### 启动序列

```
Main(picocli) 解析参数（-p/--provider、--config、--version）
  → ConfigLoader.load(路径)                          # 失败：打印原因，exit 1
  → 选定 ProviderConfig（CLI 参数 > default）         # 名字不存在：报错退出
  → thinking=true 且 protocol=openai → 一次性警告
  → ProviderFactory.create(config)
  → 组装 ConsoleUi / StreamRenderer / InMemoryHistoryStore / Conversation / 注册命令
  → ui.printBanner(provider)                          # LOGO + 供应商/模型信息
  → ConversationLoop.run()
  → 退出：renderer.close()、JLine 终端恢复（N8）
```

### 单轮对话流程

```
readLine() → null(EOF) → 退出
空输入 → 跳过
以 "/" 开头 → 命令注册表分发；返回 EXIT → 退出循环
否则：
  history.append(user)
  provider.streamChat(history.snapshot(), renderer)
    ├─ 网络线程：HTTP 流式接收 → SSE 解析 → 归一化 StreamEvent → renderer.onEvent()（入队即返回）
    └─ 渲染线程：ThinkingDelta→暗色输出 / TextDelta→正文输出 / Done→换行
  Future 完成：
    成功 → history.append(assistant 完整文本)
    失败 → ui.showError(掩码后错误) + history.removeLast() 回滚 user 消息
           （Anthropic 协议要求 user/assistant 严格交替，回滚保证下一轮请求合法）
```

### 协议要点

| | OpenAI 协议 | Anthropic 协议 |
|---|---|---|
| 端点 | `{base_url}/chat/completions` | `{base_url}/v1/messages` |
| 认证 | `Authorization: Bearer` | `x-api-key` + `anthropic-version` |
| 流结束标志 | `data: [DONE]` | `message_stop` 事件 |
| thinking | 不支持，忽略（启动时一次性警告） | `thinking:{type:enabled, budget_tokens:8192}`，此时 `max_tokens` 取 16384（未启用取 8192） |
| 正文事件 | `choices[0].delta.content` | `content_block_delta(text_delta)` |
| 思考事件 | — | `content_block_delta(thinking_delta)` |

## 文件组织

```
jasoncode-java/
├── pom.xml                              — Java 21 + 4 组依赖 + shade 打包
├── docs/phase-1/{spec,plan,task,checklist}.md
└── src/
    ├── main/java/com/jasoncode/
    │   ├── Main.java                    — picocli 入口与组装
    │   ├── config/                      — JasonConfig / ProviderConfig / Protocol
    │   │                                  ConfigLoader / ConfigException
    │   ├── provider/
    │   │   ├── ChatProvider / ChatMessage / Role / StreamEvent
    │   │   ├── StreamEventListener / ProviderException / ProviderFactory
    │   │   ├── sse/SseParser.java       — 通用按行 SSE 解析
    │   │   ├── openai/OpenAiProvider.java
    │   │   └── anthropic/AnthropicProvider.java
    │   ├── history/                     — HistoryStore / InMemoryHistoryStore
    │   ├── chat/                        — Conversation / ConversationLoop
    │   │   └── command/                 — ChatCommand / ChatContext / CommandResult
    │   │                                  ExitCommand / HelpCommand
    │   └── ui/                          — ConsoleUi / StreamRenderer / AnsiColors
    └── test/java/com/jasoncode/
        ├── config/ConfigLoaderTest.java
        ├── provider/sse/SseParserTest.java
        ├── provider/openai/OpenAiProviderTest.java     — mock HTTP 验证编解码
        ├── provider/anthropic/AnthropicProviderTest.java
        ├── history/InMemoryHistoryStoreTest.java
        └── chat/ConversationLoopTest.java              — mock provider/ui
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 构建工具 | Maven | 项目简单、依赖少，构建行为可预测；shade 插件打 fat jar 成熟稳定 |
| CLI 参数解析 | Picocli | 事实标准，注解声明参数，自带 help 输出 |
| 终端交互 | JLine 3 | 一个库解决输入历史、ANSI 颜色、终端宽度、终端状态恢复（N8）、管道检测（N4） |
| HTTP 客户端 | Java 21 内置 HttpClient | 零依赖，NIO 异步流式接收，配合事件队列不阻塞渲染 |
| SSE 解析 | 自研轻量按行解析 | 两家协议事件结构简单，自研便于测试（N6）、避免额外依赖 |
| JSON / YAML | Jackson（databind + yaml） | 一套组件同时处理协议 JSON 与配置 YAML |
| 单元测试 | JUnit 5 | 主流默认 |
| 流式架构 | 异步接收 → 线程安全事件队列 → UI 渲染线程 | 接收与渲染解耦，网络线程不被打印阻塞（N1）；二期升级 TUI 只换消费端 |
| 请求失败回滚 | history.removeLast() | Anthropic 要求 user/assistant 严格交替，失败轮次不能留下孤立 user 消息 |
| 打包运行 | maven-shade-plugin fat jar | 一期简单优先，GraalVM native-image 留作后续优化 |

## spec 覆盖核对

| spec 条目 | 归属 |
|---|---|
| F1 配置加载 | config 模块 |
| F2 供应商选择 | Main（参数解析）+ config（default） |
| F3 交互界面/LOGO/命令 | ui 模块（ConsoleUi + 命令系统） |
| F4 流式输出 | provider（SSE）+ ui（StreamRenderer） |
| F5 thinking 展示 | AnthropicProvider + StreamRenderer + Main（警告） |
| F6 多轮记忆 | history 抽象 + ConversationLoop |
| F7 Provider 抽象 | ChatProvider 接口 + ProviderFactory |
| F8 错误处理 | ProviderException + ConversationLoop 兜底 |
| N1~N8 | 见各模块职责描述 |
