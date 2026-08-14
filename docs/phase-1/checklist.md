# JasonCode（一期工程）Checklist

> 依据：已批准的 spec.md（AC1~AC11）、plan.md、task.md。
> 每一项通过运行代码或观察行为来验证，聚焦系统行为。

## 实现完整性

- [x] 配置系统：合法配置解析与全部校验（字段缺失/非法协议/重名/default 不存在）生效（验证：`mvn test -Dtest=ConfigLoaderTest` 通过）
- [x] SSE 解析器：多行 data、无 event 名事件、注释行均正确处理（验证：`mvn test -Dtest=SseParserTest` 通过）
- [x] OpenAI 协议：请求编解码、流事件解析、错误分类正确（验证：`mvn test -Dtest=OpenAiProviderTest` 通过）
- [x] Anthropic 协议：thinking 参数、thinking_delta/text_delta/message_stop 解析正确（验证：`mvn test -Dtest=AnthropicProviderTest` 通过）
- [x] 历史存储：追加/快照/回滚/清空行为正确（验证：`mvn test -Dtest=InMemoryHistoryStoreTest` 通过）
- [x] 对话循环：正常轮次、失败回滚、命令分发行为正确（验证：`mvn test -Dtest=ConversationLoopTest` 通过）
- [x] 全屏 TUI 模型层：宽度感知换行（CJK 双列）、可折叠块状态机（流式/收起/点击切换）、屏幕模型（queued 标记生命周期、思考→正文流转、鼠标命中映射）（验证：`mvn test -Dtest='TextWrapTest,CollapsibleBlockTest,ChatScreenTest'` 通过）
- [x] Lanterna 全屏 TUI：`LanternaTui` 接管备用屏幕，统一处理键盘/鼠标/尺寸事件；输入聚焦模型；Ctrl+T 折叠块切换；架构上 `ui/tui` 层替换不影响 chat/provider/config 等 Agent 层
- [x] Markdown 渲染：Assistant 回答区支持加粗/斜体/代码/列表/标题/块引用/链接/表格（GFM pipe table，含对齐与框线）的渲染（验证：`MarkdownRendererTest` 通过）
- [x] 异步对话引擎：忙碌排队、消费时合并全部未消费 prompt、失败回滚保持角色交替（验证：`mvn test -Dtest=ChatEngineTest` 通过）

## 集成

- [x] ChatProvider 统一接口被对话循环调用，两个协议实现均可经工厂创建（验证：编译 + 全部测试通过）
- [x] 事件队列解耦生效：provider 事件由渲染线程打印，网络线程不被输出阻塞（验证：真实流式对话逐块实时上屏✓ + 队列结构评审✓）
- [x] 六大模块依赖方向正确、无循环依赖（验证：编译通过 + 依赖图评审）

## 编译与测试

- [x] `mvn package` 成功，产出 `target/jasoncode.jar`
- [x] `mvn test` 全部通过（66/66）
- [x] 打包过程无 WARNING 及以上级别错误

## 端到端场景

- [x] 场景 1（AC1）：无配置文件运行 → 打印含配置路径的明确错误，非零码退出
- [x] 场景 2（AC2）：`-p notexist` 运行 → 报错指出名称不在配置中并退出
- [ ] 场景 3（AC3）：基于 Lanterna 的全屏三区布局：程序标识 Banner、流式生成期间输入框固定底部不被覆盖、全程仅一条状态栏就地刷新、四边留白底部更多、带色提示符与输入背景色条（占满 contentCols 并右对齐留白边界，空输入/多行时长度一致；点击任意输入行聚焦）；鼠标滚轮滚动历史区（事件批量处理，20ms 轮询）；输入框支持多行输入（Shift/Ctrl/Alt+Enter 换行，最多 5 行）；Assistant 回答支持 Markdown 渲染；管道降级已验证；交互效果待用户真机复验
- [x] 场景 4（AC4）：真实流式对话 → 回复逐块实时打印，肉眼可见流式过程（已由用户在真实 API 上确认）
- [x] 场景 5（AC6）：多轮记忆 → 用户实测多轮记忆功能正常
- [ ] 场景 6（AC5）：thinking: true → 思考块黄色“思考中...”实时展示，完成后自动收起为“思考内容”；Ctrl+T 快捷键展开/收起；点击展开（Lanterna 统一处理鼠标事件，待真机复验）；openai 协议配置 thinking: true → 一次性警告且对话正常
- [x] 场景 7（AC8）：错误 api_key → 打印可读错误且不退出，可继续输入下一条并正常退出
- [x] 场景 8（AC9）：错误场景的全部输出中不出现完整 API key（验证：人工检查各错误场景输出）
- [ ] 场景 9（AC10/N4）：管道方式运行无 ANSI 乱码（已验证✓）；对话中改变终端宽度：改用 Lanterna 后尺寸事件由 Screen 层统一处理，待真机复验
- [ ] 场景 10（AC11）：生成进行中连续提交多条 prompt → 界面出现 queued 标记且状态栏队列深度变化；消费时合并为一条请求且 queued 标记消失；后续上下文包含全部合并内容（合并逻辑单测✓，待真机确认）

> 注：场景 4/5/6/10 需要真实 LLM API，由用户提供可用的 `~/.jasoncode/config.yaml`；其余场景不依赖真实密钥。
