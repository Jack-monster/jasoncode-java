# JasonCode

终端 AI 助手（Coding Agent 方向的一期工程）：在终端里与大模型进行**流式多轮对话**，支持 OpenAI 与 Anthropic 两种协议，可接入任何兼容端点（Kimi、DeepSeek、GLM 等）。

启动 Banner：动漫女孩图案 + `JasonCode` 标识 + 版本/供应商信息（图案位于 `src/main/resources/banner.txt`）。

## 一期功能

- 交互式终端对话界面（Banner、带色提示符 `You ❯`、输入文本背景色区分、↑/↓ 输入历史、`/` + Tab 命令补全）
- 输入框上方状态行：当前供应商/模型、对话轮次、上下文占用，每轮刷新
- SSE 流式输出：回复逐块实时打印，首字节即上屏
- extended thinking 分块展示：`✦ Thinking` 思考块（暗色）与 `── Answer ──` 正文块结构分离
- 多轮对话记忆（会话内），退出即清空
- 统一 Provider 抽象：新增协议后端只需新增实现类
- 密钥安全：任何输出中不出现完整 API key（最多末四位）
- 管道/非终端环境自动降级为纯文本，退出时恢复终端状态

## 环境要求

- Java 21+
- Maven 3.9+

## 构建

```bash
mvn package          # 产物：target/jasoncode.jar（fat jar）
mvn test             # 运行全部单元测试（31 个）
```

## 配置

配置文件按以下顺序查找：

1. `--config` 显式指定的路径；
2. 用户目录 `~/.jasoncode/config.yaml`；
3. 运行目录 `./.jasoncode/config.yaml`。

两处默认位置都不存在时，首次运行会在 `~/.jasoncode/config.yaml` 自动生成带注释的配置模板，填写密钥后重新运行即可：

```yaml
default: kimi                     # 默认使用的供应商名

providers:
  - name: kimi                    # 供应商自己的名字（唯一标识）
    protocol: openai              # 协议：openai 或 anthropic
    model: moonshot-v1-8k         # 模型名
    base_url: https://api.moonshot.cn/v1   # 请求地址（兼容端点填自己的）
    api_key: sk-xxxxxx            # 认证密钥
  - name: claude
    protocol: anthropic
    model: claude-sonnet-4-5
    base_url: https://api.anthropic.com
    api_key: sk-ant-xxxxxx
    thinking: true                # 可选：启用扩展思考（仅 anthropic 协议有效）
```

字段说明：

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 供应商唯一标识名（如 kimi、deepseek） |
| `protocol` | 是 | 仅 `openai` / `anthropic` 两值 |
| `model` | 是 | 模型名 |
| `base_url` | 是 | API 请求地址 |
| `api_key` | 是 | 认证密钥 |
| `thinking` | 否 | 布尔开关，启用时 budget 固定 8192；openai 协议下忽略并警告 |

## 使用

```bash
java -jar target/jasoncode.jar                 # 使用 default 供应商
java -jar target/jasoncode.jar -p claude       # 临时指定供应商
java -jar target/jasoncode.jar --config my.yaml
java -jar target/jasoncode.jar --help
```

会话内命令：

| 命令 | 说明 |
|------|------|
| `/help` | 列出可用命令 |
| `/exit` | 退出（也可用 Ctrl+C / Ctrl+D） |

## 项目结构

```
src/main/java/com/jasoncode/
├── Main.java                 — picocli 入口与组装
├── config/                   — YAML 配置加载与校验
├── provider/                 — 统一 ChatProvider 抽象 + SSE 解析
│   ├── openai/               — OpenAI 协议实现
│   └── anthropic/            — Anthropic 协议实现（含 extended thinking）
├── history/                  — 历史存储抽象（一期内存实现，预留持久化接口）
├── chat/                     — 对话循环与可扩展命令系统
└── ui/                       — JLine 终端交互 + 事件队列流式渲染
```

## 文档

一期工程遵循 Spec 驱动开发流程，四份文档见 `docs/phase-1/`：

- [spec.md](docs/phase-1/spec.md) — 做什么（需求与验收标准）
- [plan.md](docs/phase-1/plan.md) — 怎么做（架构与接口设计）
- [task.md](docs/phase-1/task.md) — 按什么顺序做（14 个任务）
- [checklist.md](docs/phase-1/checklist.md) — 做对了没（验收清单）

## 一期范围之外（后续工程）

tool use / 文件操作 / 代码编辑、会话持久化、多行输入、Markdown 渲染、会话切换 UI、其他协议。
