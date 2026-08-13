package com.jasoncode.provider.sse;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SseParserTest {

    record Captured(String event, String data) {
    }

    private final List<Captured> events = new ArrayList<>();
    private final SseParser parser = new SseParser((event, data) -> events.add(new Captured(event, data)));

    private void feed(String... lines) {
        for (String line : lines) {
            parser.acceptLine(line);
        }
    }

    @Test
    void parsesTwoStandardEvents() {
        feed(
                "event: message_start",
                "data: {\"type\":\"message_start\"}",
                "",
                "event: message_stop",
                "data: {\"type\":\"message_stop\"}",
                ""
        );

        assertEquals(2, events.size());
        assertEquals(new Captured("message_start", "{\"type\":\"message_start\"}"), events.get(0));
        assertEquals(new Captured("message_stop", "{\"type\":\"message_stop\"}"), events.get(1));
    }

    @Test
    void joinsMultiLineDataWithNewline() {
        feed(
                "data: line-one",
                "data: line-two",
                ""
        );

        assertEquals(1, events.size());
        assertEquals("line-one\nline-two", events.get(0).data());
    }

    @Test
    void dataOnlyEventHasNullEventName() {
        feed(
                "data: {\"choices\":[]}",
                ""
        );

        assertEquals(1, events.size());
        assertNull(events.get(0).event());
        assertEquals("{\"choices\":[]}", events.get(0).data());
    }

    @Test
    void ignoresCommentsAndBlankLinesWithoutData() {
        feed(
                ": keep-alive ping",
                "",
                "",
                "data: real",
                ""
        );

        assertEquals(1, events.size());
        assertEquals("real", events.get(0).data());
    }

    @Test
    void flushDispatchesTrailingEventWithoutBlankLine() {
        feed("data: last");
        assertEquals(0, events.size(), "未遇到空行不应提前派发");

        parser.flush();
        assertEquals(1, events.size());
        assertEquals("last", events.get(0).data());
    }

    @Test
    void eventNameDoesNotLeakIntoNextEvent() {
        feed(
                "event: first",
                "data: a",
                "",
                "data: b",
                ""
        );

        assertEquals("first", events.get(0).event());
        assertNull(events.get(1).event());
    }
}
