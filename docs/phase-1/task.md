# JasonCode（一期工程）Tasks

> 依据：已批准的 `docs/phase-1/spec.md` 与 `docs/phase-1/plan.md`。

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `pom.xml` | Java 21、依赖（picocli / jline / jackson-databind / jackson-dataformat-yaml / junit5）、shade 打包 |
| 新建 | `src/main/java/com/jasoncode/config/Protocol.java` | 协议枚举 |
| 新建 | `src/main/java/com/jasoncode/config/ProviderConfig.java` | 单个供应商配置（含掩码 toString） |
| 新建 | `src/main/java/com/jasoncode/config/JasonConfig.java` | 顶层配置（default + providers） |
| 新建 | `src/main/java/com/jasoncode/config/ConfigException.java` | 配置错误（人类可读原因） |
| 新建 | `src/main/java/com/jasoncode/config/ConfigLoader.java` | YAML 加载与校验 |
| 新建 | `src/main/java/com/jasoncode/provider/Role.java` | 消息角色枚举 |
| 新建 | `src/main/java/com/jasoncode/provider/ChatMessage.java` | 对话消息 |
| 新建 | `src/main/java/com/jasoncode/provider/StreamEvent.java` | 归一化流式事件（sealed） |
| 新建 | `src/main/java/com/jasoncode/provider/StreamEventListener.java` | 事件监听器接口 |
| 新建 | `src/main/java/com/jasoncode/provider/ProviderException.java` | Provider 异常（分类 + 掩码安全） |
| 新建 | `src/main/java/com/jasoncode/provider/ChatProvider.java` | 统一 Provider 接口 |
| 新建 | `src/main/java/com/jasoncode/provider/sse/SseParser.java` | 通用按行 SSE 解析 |
| 新建 | `src/main/java/com/jasoncode/provider/openai/OpenAiProvider.java` | OpenAI 协议实现 |
| 新建 | `src/main/java/com/jasoncode/provider/anthropic/AnthropicProvider.java` | Anthropic 协议实现 |
| 新建 | `src/main/java/com/jasoncode/provider/ProviderFactory.java` | 按 protocol 创建实例 |
| 新建 | `src/main/java/com/jasoncode/history/HistoryStore.java` | 历史存取接口 |
| 新建 | `src/main/java/com/jasoncode/history/InMemoryHistoryStore.java` | 内存实现 |
| 新建 | `src/main/java/com/jasoncode/ui/AnsiColors.java` | ANSI 颜色 + 管道降级 |
| 新建 | `src/main/java/com/jasoncode/ui/StreamRenderer.java` | 事件队列 + 渲染线程 |
| 新建 | `src/main/java/com/jasoncode/ui/ConsoleUi.java` | JLine 输入 / LOGO / 错误输出 |
| 新建 | `src/main/java/com/jasoncode/chat/Conversation.java` | 单个会话对象 |
| 新建 | `src/main/java/com/jasoncode/chat/ConversationLoop.java` | 对话主循环 |
| 新建 | `src/main/java/com/jasoncode/chat/command/ChatCommand.java` | 命令接口 |
| 新建 | `src/main/java/com/jasoncode/chat/command/CommandResult.java` | 命令执行结果 |
| 新建 | `src/main/java/com/jasoncode/chat/command/ChatContext.java` | 命令执行上下文 |
| 新建 | `src/main/java/com/jasoncode/chat/command/ExitCommand.java` | /exit |
| 新建 | `src/main/java/com/jasoncode/chat/command/HelpCommand.java` | /help |
| 新建 | `src/main/java/com/jasoncode/Main.java` | picocli 入口与组装 |
| 新建 | `src/test/java/com/jasoncode/config/ConfigLoaderTest.java` | 配置加载测试 |
| 新建 | `src/test/java/com/jasoncode/provider/sse/SseParserTest.java` | SSE 解析测试 |
| 新建 | `src/test/java/com/jasoncode/provider/openai/OpenAiProviderTest.java` | OpenAI 协议测试（mock SSE 服务） |
| 新建 | `src/test/java/com/jasoncode/provider/anthropic/AnthropicProviderTest.java` | Anthropic 协议测试（mock SSE 服务） |
| 新建 | `src/test/java/com/jasoncode/history/InMemoryHistoryStoreTest.java` | 历史存储测试 |
| 新建 | `src/test/java/com/jasoncode/chat/ConversationLoopTest.java` | 对话循环测试（mock provider/ui） |

## T1: 项目骨架

**文件：** `pom.xml`
**依赖：** 无
**步骤：**
1. 创建 Maven 工程：groupId `com.jasoncode`，artifactId `jasoncode`，Java 21（`maven.compiler.release=21`，UTF-8）
2. 添加依赖：`info.picocli:picocli`、`org.jline:jline`、`com.fasterxml.jackson.core:jackson-databind`、`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`、`org.junit.jupiter:junit-jupiter`（test scope）
3. 配置插件：`maven-compiler-plugin`、`maven-surefire-plugin`（JUnit 5）、`maven-shade-plugin`（Main-Class 指向 `com.jasoncode.Main`，finalName `jasoncode`）
4. 创建 `src/main/java`、`src/test/java` 目录

**验证：** 运行 `mvn -q compile`，无错误退出

## T2: config 模块

**文件：** `src/main/java/com/jasoncode/config/` 下 5 个文件
**依赖：** T1
**步骤：**
1. `Protocol` 枚举：`OPENAI`、`ANTHROPIC`；提供从字符串解析的方法，非法值抛异常
2. `ProviderConfig` record：name / protocol / model / baseUrl（映射 `base_url`）/ apiKey（映射 `api_key`）/ thinking（默认 false）；重写 `toString()` 将 apiKey 掩码为 `****` + 末四位（不足四位全掩）
3. `JasonConfig` record：defaultProvider（映射 `default`）+ providers 列表
4. `ConfigException`：携带人类可读原因
5. `ConfigLoader.load(Path)`：用 Jackson YAML 读取；依次校验——文件存在且可读、providers 非空、每个供应商的 name/protocol/model/base_url/api_key 非空、protocol 合法、name 不重复、default 存在于 providers 中；任一失败抛 ConfigException（指明出错字段与供应商名）

**验证：** 运行 `mvn -q compile`，无错误退出

## T3: config 单元测试

**文件：** `src/test/java/com/jasoncode/config/ConfigLoaderTest.java`
**依赖：** T2
**步骤：**
1. 编写合法 YAML fixture（两个供应商：一个 openai 协议、一个 anthropic 协议含 thinking），断言全部字段正确解析
2. 用例：配置文件不存在 → ConfigException 且消息包含路径
3. 用例：缺少 api_key → ConfigException 且消息指明字段与供应商名
4. 用例：protocol 非法值 → ConfigException
5. 用例：name 重复 → ConfigException
6. 用例：default 指向不存在的 name → ConfigException
7. 用例：ProviderConfig.toString() 不出现完整密钥（仅末四位）

**验证：** 运行 `mvn -q test -Dtest=ConfigLoaderTest`，全部通过

## T4: provider 核心模型

**文件：** `src/main/java/com/jasoncode/provider/` 下 6 个文件
**依赖：** T1
**步骤：**
1. `Role` 枚举：USER、ASSISTANT
2. `ChatMessage` record：role + content
3. `StreamEvent` sealed interface：ThinkingDelta / TextDelta / Done / Error 四个实现
4. `StreamEventListener` 接口：`onEvent(StreamEvent)`
5. `ProviderException`：分类枚举（NETWORK / AUTH / API / PARSE）+ 人类可读 message；确保 message 构造路径不含完整 apiKey
6. `ChatProvider` 接口：`CompletableFuture<Void> streamChat(List<ChatMessage>, StreamEventListener)`、`String describe()`

**验证：** 运行 `mvn -q compile`，无错误退出

## T5: SSE 解析器

**文件：** `src/main/java/com/jasoncode/provider/sse/SseParser.java`、`src/test/java/com/jasoncode/provider/sse/SseParserTest.java`
**依赖：** T4
**步骤：**
1. 实现 `SseParser`：消费逐行文本（`event:`、`data:`、空行分隔事件），将每个完整 SSE 事件以（event 名, data 内容）回调给调用方；支持 data 多行拼接、忽略注释行、容忍 `event:` 缺省
2. 测试：标准双事件流 → 回调两次且内容正确
3. 测试：多行 data 拼接正确
4. 测试：无 event 名的纯 data 事件正确回调
5. 测试：注释行与空行被正确忽略

**验证：** 运行 `mvn -q test -Dtest=SseParserTest`，全部通过

## T6: OpenAI 协议实现

**文件：** `src/main/java/com/jasoncode/provider/openai/OpenAiProvider.java`、`src/test/java/com/jasoncode/provider/openai/OpenAiProviderTest.java`
**依赖：** T5
**步骤：**
1. 构造请求：POST `{base_url}/chat/completions`，请求头 `Authorization: Bearer {api_key}`、`Content-Type: application/json`，请求体含 model、messages（role 小写）、`stream: true`
2. 用 Java 21 HttpClient 发起异步请求，逐行读取响应体，交给 SseParser
3. 事件处理：解析 `choices[0].delta.content` 非空时发 TextDelta；收到 `data: [DONE]` 发 Done；HTTP 401/403 → AUTH 错误；其他 HTTP 错误读取 body 提取 error.message → API 错误；连接异常 → NETWORK 错误；均通过 `Error` 事件 + Future 异常完成
4. 测试：JDK `com.sun.net.httpserver.HttpServer` 起 mock 端点，返回预置 SSE 流（两段 delta + DONE），断言依次收到 TextDelta×2、Done，且请求体含 `stream:true` 与正确 Authorization 头
5. 测试：mock 返回 401 → Future 以 AUTH 分类的 ProviderException 完成，且异常消息不含密钥
6. 测试：mock 返回 400 + `{"error":{"message":"bad model"}}` → API 错误消息包含 "bad model"

**验证：** 运行 `mvn -q test -Dtest=OpenAiProviderTest`，全部通过

## T7: Anthropic 协议实现

**文件：** `src/main/java/com/jasoncode/provider/anthropic/AnthropicProvider.java`、`src/test/java/com/jasoncode/provider/anthropic/AnthropicProviderTest.java`
**依赖：** T5
**步骤：**
1. 构造请求：POST `{base_url}/v1/messages`，请求头 `x-api-key`、`anthropic-version: 2023-06-01`、`Content-Type: application/json`；请求体含 model、messages、`stream: true`、max_tokens（thinking 开启取 16384，否则 8192）；thinking 开启时追加 `thinking: {type: "enabled", budget_tokens: 8192}`
2. 用 Java 21 HttpClient 异步流式接收，交给 SseParser
3. 事件处理：`content_block_delta` 且 delta.type 为 `thinking_delta` → ThinkingDelta；为 `text_delta` → TextDelta；`message_stop` → Done；`error` 事件与 HTTP 错误（401/403 → AUTH，其余 → API）按 OpenAI 实现同样模式翻译；连接异常 → NETWORK
4. 测试：mock 端点返回预置 SSE 流（thinking_delta ×1 + text_delta ×2 + message_stop），断言事件序列正确，且请求体含 thinking 配置（开启场景）或不含（关闭场景）
5. 测试：mock 返回 401 → AUTH 错误且消息不含密钥
6. 测试：mock 返回 `event: error` 事件 → API 错误且消息可读

**验证：** 运行 `mvn -q test -Dtest=AnthropicProviderTest`，全部通过

## T8: ProviderFactory

**文件：** `src/main/java/com/jasoncode/provider/ProviderFactory.java`
**依赖：** T6、T7
**步骤：**
1. `create(ProviderConfig)`：OPENAI → OpenAiProvider；ANTHROPIC → AnthropicProvider；其他抛 IllegalArgumentException
2. 共享一个 HttpClient 实例（连接复用）

**验证：** 运行 `mvn -q compile` 无错误；运行 `mvn -q test`，已有全部测试仍通过

## T9: history 模块

**文件：** `src/main/java/com/jasoncode/history/` 下 2 个文件、`src/test/java/com/jasoncode/history/InMemoryHistoryStoreTest.java`
**依赖：** T4
**步骤：**
1. `HistoryStore` 接口：append / snapshot（不可变副本）/ removeLast / clear
2. `InMemoryHistoryStore`：ArrayList 实现，snapshot 返回不可变列表
3. 测试：追加后 snapshot 顺序正确；snapshot 不可变（修改抛异常）；removeLast 移除末条且空库调用不抛异常；clear 后为空

**验证：** 运行 `mvn -q test -Dtest=InMemoryHistoryStoreTest`，全部通过

## T10: ui 模块

**文件：** `src/main/java/com/jasoncode/ui/` 下 3 个文件
**依赖：** T4
**步骤：**
1. `AnsiColors`：检测输出是否为终端（`System.console()` 或 JLine terminal 类型），非终端时所有着色方法退化为原文；提供 dim / cyan / red / yellow / bold / reset 方法
2. `StreamRenderer`：实现 StreamEventListener；内部 LinkedBlockingQueue 存事件、专用渲染线程消费；ThinkingDelta → dim 色输出；TextDelta → 正常输出并累积本轮文本；Done → 输出换行并置完成标记；start/awaitDrain/takeTurnText/close 按 plan.md 签名实现；close 时中断线程
3. `ConsoleUi`：JLine Terminal + LineReader（开启输入历史）；`printBanner(provider)` 打印 ASCII LOGO 与 provider.describe()；`readLine()` 用提示符读取，EOF 返回 null；`showError`/`showWarning` 红/黄色输出到 stderr 或主输出
4. Ctrl+C 处理：注册 JLine 信号处理或 shutdown hook，确保终端状态恢复

**验证：** 运行 `mvn -q compile`，无错误退出

## T11: 命令系统

**文件：** `src/main/java/com/jasoncode/chat/command/` 下 5 个文件
**依赖：** T10
**步骤：**
1. `ChatCommand` 接口（name / description / execute）、`CommandResult` 枚举（CONTINUE / EXIT）
2. `ChatContext`：命令可访问的上下文（当前含 ConsoleUi 引用，为后续会话管理预留）
3. `ExitCommand`：返回 EXIT
4. `HelpCommand`：遍历已注册命令打印 name 与 description
5. 命令注册表（Map<String, ChatCommand>）与分发方法：输入以 `/` 开头时按名字查找；未知命令打印提示并返回 CONTINUE

**验证：** 运行 `mvn -q compile`，无错误退出

## T12: 对话循环

**文件：** `src/main/java/com/jasoncode/chat/` 下 2 个文件、`src/test/java/com/jasoncode/chat/ConversationLoopTest.java`
**依赖：** T9、T10、T11
**步骤：**
1. `Conversation`：持有 HistoryStore
2. `ConversationLoop`：主循环按 plan.md「单轮对话流程」实现——readLine null → 退出；空输入跳过；`/` 开头 → 命令分发；否则 append user → streamChat → awaitDrain → 成功则 takeTurnText 后 append assistant，Future 异常则 showError + removeLast 回滚
3. 测试（mock ChatProvider 与 ConsoleUi）：两轮对话后 history 含 user/assistant 交替四条；provider 失败时 user 消息被回滚且循环继续；输入 `/exit` 退出循环；空输入不触发 provider 调用

**验证：** 运行 `mvn -q test -Dtest=ConversationLoopTest`，全部通过

## T13: Main 入口

**文件：** `src/main/java/com/jasoncode/Main.java`
**依赖：** T2、T8、T12
**步骤：**
1. picocli `@Command`：选项 `-p/--provider`、`--config`（默认 `~/.jasoncode/config.yaml`）、`--version`
2. 启动序列按 plan.md 实现：加载配置（失败打印原因 exit 1）→ 选定供应商（参数 > default，不存在报错退出）→ openai + thinking=true 时一次性警告 → ProviderFactory.create → 组装 ConsoleUi / StreamRenderer / Conversation / 注册命令 → printBanner → loop.run()
3. 退出清理：renderer.close()、JLine terminal close
4. 全局兜底：未捕获异常打印可读消息（不含密钥）后以非零码退出

**验证：** 运行 `mvn -q compile` 无错误；运行 `mvn -q test`，全部测试通过

## T14: 打包与冒烟验证

**文件：** 无新增（验证任务）
**依赖：** T13
**步骤：**
1. 运行 `mvn -q package`，确认产出 `target/jasoncode.jar`
2. 无配置文件运行 `java -jar target/jasoncode.jar` → 期望打印含路径的错误信息并以非零码退出
3. 写入一个合法但 api_key 为假值的测试配置，运行 `--help` → 期望打印用法说明
4. 用 `-p notexist` 运行 → 期望报错指出该名称不在配置中并退出
5. 管道方式运行（`echo "" | java -jar ...`）→ 期望无 ANSI 乱码

**验证：** 上述 5 步实际运行输出均符合预期（命令输出作为证据留存）

## 执行顺序

```
T1 → T2 → T3
 │
 ├→ T4 → T5 → T6（可与 T7 并行）→ T8 ─┐
 │        └─→ T7 ──────────────────────┤
 ├→ T9（可并行）────────────────────────┤
 └→ T10 → T11 ─────────────────────────┴→ T12 → T13 → T14
```
