package com.jasoncode.provider.openai;

import com.jasoncode.config.Protocol;
import com.jasoncode.config.ProviderConfig;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ProviderException;
import com.jasoncode.provider.StreamEvent;
import com.sun.net.httpserver.HttpExchange;
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

class OpenAiProviderTest {

    private static final String API_KEY = "sk-test-key-123456";

    private HttpServer server;
    private OpenAiProvider provider;
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        ProviderConfig config = new ProviderConfig(
                "mock-openai", Protocol.OPENAI, "gpt-4o-mini",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                API_KEY, false);
        provider = new OpenAiProvider(config, OpenAiProvider.defaultHttpClient(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void mockResponse(int status, String body) {
        server.createContext("/v1/chat/completions", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
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

    private static String chunk(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }

    @Test
    void streamsTextDeltasThenDone() throws Exception {
        mockResponse(200, chunk("Hello") + chunk(" world") + "data: [DONE]\n\n");

        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        provider.streamChat(List.of(ChatMessage.user("hi")), events::add).get();

        assertEquals(3, events.size());
        assertEquals(new StreamEvent.TextDelta("Hello"), events.get(0));
        assertEquals(new StreamEvent.TextDelta(" world"), events.get(1));
        assertInstanceOf(StreamEvent.Done.class, events.get(2));

        assertEquals("/v1/chat/completions", capturedPath.get());
        assertEquals("Bearer " + API_KEY, capturedAuth.get());
        assertTrue(capturedBody.get().contains("\"stream\":true"));
        assertTrue(capturedBody.get().contains("\"model\":\"gpt-4o-mini\""));
        assertTrue(capturedBody.get().contains("\"role\":\"user\""));
    }

    @Test
    void http401MapsToAuthErrorWithoutLeakingKey() {
        mockResponse(401, "{\"error\":{\"message\":\"invalid key\"}}");

        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        var future = provider.streamChat(List.of(ChatMessage.user("hi")), events::add);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);

        ProviderException pe = assertInstanceOf(ProviderException.class, ex.getCause());
        assertEquals(ProviderException.Category.AUTH, pe.category());
        assertFalse(pe.getMessage().contains(API_KEY), "错误消息不得包含完整密钥");
        assertTrue(events.stream().anyMatch(e -> e instanceof StreamEvent.Error));
    }

    @Test
    void http400MapsToApiErrorWithServerMessage() {
        mockResponse(400, "{\"error\":{\"message\":\"bad model\"}}");

        var future = provider.streamChat(List.of(ChatMessage.user("hi")), e -> {
        });
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);

        ProviderException pe = assertInstanceOf(ProviderException.class, ex.getCause());
        assertEquals(ProviderException.Category.API, pe.category());
        assertTrue(pe.getMessage().contains("bad model"));
    }

    @Test
    void connectionRefusedMapsToNetworkError() {
        server.stop(0); // 端口不再可用
        var future = provider.streamChat(List.of(ChatMessage.user("hi")), e -> {
        });
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);

        ProviderException pe = assertInstanceOf(ProviderException.class, ex.getCause());
        assertEquals(ProviderException.Category.NETWORK, pe.category());
    }
}
