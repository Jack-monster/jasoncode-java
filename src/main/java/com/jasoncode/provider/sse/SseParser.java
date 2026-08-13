package com.jasoncode.provider.sse;

/**
 * 通用按行 SSE 解析器。
 * <p>
 * 调用方逐行喂入响应文本（不含行终止符）；解析器按 SSE 规范聚合成
 * 完整事件后回调：空行分隔事件，{@code event:} 指定事件名（可缺省），
 * 多个 {@code data:} 行以换行拼接，{@code :} 开头为注释行。
 */
public final class SseParser {

    @FunctionalInterface
    public interface EventHandler {
        /**
         * @param event 事件名，无 event: 行时为 null
         * @param data  data 内容（多行已用 \n 拼接）
         */
        void onEvent(String event, String data);
    }

    private final EventHandler handler;
    private String eventName;
    private StringBuilder dataBuffer;

    public SseParser(EventHandler handler) {
        this.handler = handler;
    }

    /** 喂入一行（不含行终止符）。 */
    public void acceptLine(String line) {
        if (line.isEmpty()) {
            dispatch();
            return;
        }
        if (line.startsWith(":")) {
            return; // 注释行（含 keep-alive）
        }
        String field;
        String value;
        int colon = line.indexOf(':');
        if (colon < 0) {
            field = line;
            value = "";
        } else {
            field = line.substring(0, colon);
            value = line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
        }
        switch (field) {
            case "event" -> eventName = value;
            case "data" -> {
                if (dataBuffer == null) {
                    dataBuffer = new StringBuilder();
                } else {
                    dataBuffer.append('\n');
                }
                dataBuffer.append(value);
            }
            default -> {
                // id / retry 等字段一期不处理
            }
        }
    }

    /** 流结束时调用：若仍有未派发的事件则派发。 */
    public void flush() {
        dispatch();
    }

    private void dispatch() {
        if (dataBuffer == null) {
            eventName = null;
            return;
        }
        String data = dataBuffer.toString();
        String event = eventName;
        eventName = null;
        dataBuffer = null;
        handler.onEvent(event, data);
    }
}
