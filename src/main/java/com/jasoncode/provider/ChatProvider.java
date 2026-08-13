package com.jasoncode.provider;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 统一 Provider 接口（F7）：屏蔽 OpenAI / Anthropic 协议差异。
 * 新增协议后端只需新增实现，不修改上层代码。
 */
public interface ChatProvider {

    /**
     * 发起流式对话。事件通过 listener 回调（可能来自其他线程），
     * 返回的 Future 在流结束（成功或失败）时完成；失败时以 ProviderException 结束。
     *
     * @param history  完整对话历史（调用方保证角色交替合法）
     * @param listener 流式事件监听器
     */
    CompletableFuture<Void> streamChat(List<ChatMessage> history, StreamEventListener listener);

    /** 人类可读的供应商标识，如 "claude (anthropic / claude-sonnet-4-5)"，用于启动横幅。 */
    String describe();
}
