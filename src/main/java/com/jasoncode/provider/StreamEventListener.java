package com.jasoncode.provider;

/**
 * 流式事件监听器：ui 侧实现它，把事件投入渲染队列。
 */
public interface StreamEventListener {

    void onEvent(StreamEvent event);
}
