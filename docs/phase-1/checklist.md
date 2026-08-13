# JasonCode（一期工程）Checklist

> 依据：已批准的 spec.md（AC1~AC10）、plan.md、task.md。
> 每一项通过运行代码或观察行为来验证，聚焦系统行为。

## 实现完整性

- [ ] 配置系统：合法配置解析与全部校验（字段缺失/非法协议/重名/default 不存在）生效（验证：`mvn test -Dtest=ConfigLoaderTest` 通过）
- [ ] SSE 解析器：多行 data、无 event 名事件、注释行均正确处理（验证：`mvn test -Dtest=SseParserTest` 通过）
- [ ] OpenAI 协议：请求编解码、流事件解析、错误分类正确（验证：`mvn test -Dtest=OpenAiProviderTest` 通过）
- [ ] Anthropic 协议：thinking 参数、thinking_delta/text_delta/message_stop 解析正确（验证：`mvn test -Dtest=AnthropicProviderTest` 通过）
- [ ] 历史存储：追加/快照/回滚/清空行为正确（验证：`mvn test -Dtest=InMemoryHistoryStoreTest` 通过）
- [ ] 对话循环：正常轮次、失败回滚、命令分发行为正确（验证：`mvn test -Dtest=ConversationLoopTest` 通过）

## 集成

- [ ] ChatProvider 统一接口被对话循环调用，两个协议实现均可经工厂创建（验证：编译 + 全部测试通过）
- [ ] 事件队列解耦生效：provider 事件由渲染线程打印，网络线程不被输出阻塞（验证：真实流式对话中逐块实时上屏 + 代码评审队列结构）
- [ ] 六大模块依赖方向正确、无循环依赖（验证：编译通过 + 依赖图评审）

## 编译与测试

- [ ] `mvn package` 成功，产出 `target/jasoncode.jar`
- [ ] `mvn test` 全部通过
- [ ] 打包过程无 WARNING 及以上级别错误

## 端到端场景

- [ ] 场景 1（AC1）：无配置文件运行 → 打印含配置路径的明确错误，非零码退出
- [ ] 场景 2（AC2）：`-p notexist` 运行 → 报错指出名称不在配置中并退出
- [ ] 场景 3（AC3）：启动显示 LOGO 与供应商/模型信息；`/help` 列出命令；`/exit` 退出后终端布局完好；↑/↓ 可翻看输入历史
- [ ] 场景 4（AC4）：真实流式对话 → 回复逐块实时打印，肉眼可见流式过程（需真实可用的 provider 配置）
- [ ] 场景 5（AC6）：多轮记忆 → 第一轮告知 AI 一个事实，第二轮就该事实提问得到正确回答
- [ ] 场景 6（AC5）：thinking: true（anthropic 协议）→ 思考内容实时显示且与正文视觉区分；openai 协议配置 thinking: true → 一次性警告且对话正常
- [ ] 场景 7（AC8）：错误 api_key → 打印可读错误且不退出，可继续输入下一条并正常退出
- [ ] 场景 8（AC9）：错误场景的全部输出中不出现完整 API key（验证：人工检查各错误场景输出）
- [ ] 场景 9（AC10/N4）：管道方式运行无 ANSI 乱码；对话中改变终端宽度，后续输出显示正常

> 注：场景 4/5/6 需要真实 LLM API，由用户提供可用的 `~/.jasoncode/config.yaml`；其余场景不依赖真实密钥。
