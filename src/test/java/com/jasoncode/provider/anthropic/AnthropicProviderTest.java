package com.jasoncode.provider.anthropic;

import com.jasoncode.config.Protocol;
import com.jasoncode.config.ProviderConfig;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ProviderException;
import com.jasoncode.provider.StreamEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicProviderTest {

    private static final String API_KEY = "sk-ant-test-key-9876";

    private HttpServer server;
    private final AtomicReference<String> capturedApiKey = new AtomicReference<>();
    private final AtomicReference<String> capturedVersion = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private AnthropicProvider provider(boolean thinking) {
        ProviderConfig config = new ProviderConfig(
                "mock-claude", Protocol.ANTHROPIC, "claude-sonnet-4-5",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                API_KEY, thinking);
        return new AnthropicProvider(config, AnthropicProvider.defaultHttpClient(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private void mockResponse(int status, String body) {
        server.createContext("/v1/messages", exchange -> {
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            capturedVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data + "\n\n";
    }

    @Test
    void streamsThinkingThenTextThenDone() throws Exception {
        mockResponse(200,
                event("content_block_delta",
                        "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"let me think\"}}")
                        + event("content_block_delta",
                        "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}")
                        + event("content_block_delta",
                        "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" Claude\"}}")
                        + event("message_stop", "{\"type\":\"message_stop\"}"));

        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        provider(true).streamChat(List.of(ChatMessage.user("hi")), events::add).get();

        assertEquals(4, events.size());
        assertEquals(new StreamEvent.ThinkingDelta("let me think"), events.get(0));
        assertEquals(new StreamEvent.TextDelta("Hello"), events.get(1));
        assertEquals(new StreamEvent.TextDelta(" Claude"), events.get(2));
        assertInstanceOf(StreamEvent.Done.class, events.get(3));

        assertEquals("/v1/messages", capturedPath.get());
        assertEquals(API_KEY, capturedApiKey.get());
        assertEquals("2023-06-01", capturedVersion.get());
        assertTrue(capturedBody.get().contains("\"budget_tokens\":8192"));
        assertTrue(capturedBody.get().contains("\"max_tokens\":16384"));
    }

    @Test
    void thinkingDisabledOmitsThinkingBlock() throws Exception {
        mockResponse(200,
                event("content_block_delta",
                        "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"ok\"}}")
                        + event("message_stop", "{\"type\":\"message_stop\"}"));

        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        provider(false).streamChat(List.of(ChatMessage.user("hi")), events::add).get();

        assertFalse(capturedBody.get().contains("thinking"), "未启用 thinking 时请求体不应包含 thinking");
        assertTrue(capturedBody.get().contains("\"max_tokens\":8192"));
        assertEquals(2, events.size());
    }

    @Test
    void http401MapsToAuthErrorWithoutLeakingKey() {
        mockResponse(401, "{\"error\":{\"message\":\"invalid x-api-key\"}}");

        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        var future = provider(false).streamChat(List.of(ChatMessage.user("hi")), events::add);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);

        ProviderException pe = assertInstanceOf(ProviderException.class, ex.getCause());
        assertEquals(ProviderException.Category.AUTH, pe.category());
        assertFalse(pe.getMessage().contains(API_KEY), "错误消息不得包含完整密钥");
    }

    @Test
    void errorEventMapsToApiError() {
        mockResponse(200,
                event("error", "{\"type\":\"error\",\"error\":{\"message\":\"overloaded\"}}"));

        var future = provider(false).streamChat(List.of(ChatMessage.user("hi")), e -> {
        });
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);

        ProviderException pe = assertInstanceOf(ProviderException.class, ex.getCause());
        assertEquals(ProviderException.Category.API, pe.category());
        assertTrue(pe.getMessage().contains("overloaded"));
    }
}
