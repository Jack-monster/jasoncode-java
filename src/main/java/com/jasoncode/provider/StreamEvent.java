package com.jasoncode.provider;

/**
 * 归一化流式事件：上层（ui/chat）只认这个，不感知协议细节。
 */
public sealed interface StreamEvent {

    /** 扩展思考内容增量（仅 Anthropic thinking 启用时产生）。 */
    record ThinkingDelta(String text) implements StreamEvent {
    }

    /** 正文内容增量。 */
    record TextDelta(String text) implements StreamEvent {
    }

    /** 本轮生成正常结束。 */
    record Done() implements StreamEvent {
    }

    /** 本轮生成失败。 */
    record Error(ProviderException error) implements StreamEvent {
    }
}
