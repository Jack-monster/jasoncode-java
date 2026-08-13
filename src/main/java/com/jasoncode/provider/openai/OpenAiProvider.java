package com.jasoncode.provider.openai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jasoncode.config.ProviderConfig;
import com.jasoncode.provider.ChatMessage;
import com.jasoncode.provider.ChatProvider;
import com.jasoncode.provider.ProviderException;
import com.jasoncode.provider.StreamEvent;
import com.jasoncode.provider.StreamEventListener;
import com.jasoncode.provider.sse.SseParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * OpenAI 协议（/chat/completions）的 ChatProvider 实现，
 * 同时适用于任何 OpenAI 兼容端点。
 */
public final class OpenAiProvider implements ChatProvider {

    private static final String DONE_DATA = "[DONE]";

    private final ProviderConfig config;
    private final HttpClient httpClient;
    private final Executor executor;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OpenAiProvider(ProviderConfig config, HttpClient httpClient, Executor executor) {
        this.config = config;
        this.httpClient = httpClient;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> streamChat(List<ChatMessage> history, StreamEventListener listener) {
        HttpRequest request;
        try {
            request = buildRequest(history);
        } catch (Exception e) {
            return fail(listener, new ProviderException(ProviderException.Category.API,
                    "构造请求失败：" + e.getMessage(), e));
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApplyAsync(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw handleHttpError(response);
                    }
                    consumeStream(response.body(), listener);
                    return (Void) null;
                }, executor)
                .exceptionally(t -> {
                    ProviderException error = toProviderException(t);
                    listener.onEvent(new StreamEvent.Error(error));
                    throw new CompletionException(error);
                });
    }

    @Override
    public String describe() {
        return config.name() + " (" + config.protocol().name().toLowerCase() + " / " + config.model() + ")";
    }

    private HttpRequest buildRequest(List<ChatMessage> history) {
        ObjectNode body = json.createObjectNode();
        body.put("model", config.model());
        body.put("stream", true);
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage message : history) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role().wire());
            node.put("content", message.content());
        }
        String url = trimTrailingSlash(config.baseUrl()) + "/chat/completions";
        return HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private void consumeStream(InputStream body, StreamEventListener listener) {
        SseParser parser = new SseParser((event, data) -> handleData(data, listener));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parser.acceptLine(line);
            }
            parser.flush();
            listener.onEvent(new StreamEvent.Done());
        } catch (IOException e) {
            throw new ProviderException(ProviderException.Category.NETWORK,
                    "读取流式响应中断：" + e.getMessage(), e);
        }
    }

    private void handleData(String data, StreamEventListener listener) {
        if (DONE_DATA.equals(data.trim())) {
            return; // Done 由流读完后统一发出
        }
        try {
            JsonNode node = json.readTree(data);
            JsonNode content = node.at("/choices/0/delta/content");
            if (!content.isMissingNode() && content.isTextual() && !content.asText().isEmpty()) {
                listener.onEvent(new StreamEvent.TextDelta(content.asText()));
            }
        } catch (Exception ignore) {
            // 宽容处理无法解析的 chunk（不同兼容端点可能夹杂私有事件）
        }
    }

    private ProviderException handleHttpError(HttpResponse<InputStream> response) {
        String detail = readBodySilently(response.body());
        String message = extractErrorMessage(detail);
        int status = response.statusCode();
        ProviderException.Category category = (status == 401 || status == 403)
                ? ProviderException.Category.AUTH
                : ProviderException.Category.API;
        String text = category == ProviderException.Category.AUTH
                ? "认证失败 (HTTP " + status + ")：请检查供应商 " + config.name() + " 的 api_key"
                : "API 错误 (HTTP " + status + ")：" + message;
        return new ProviderException(category, text);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "服务端未返回错误详情";
        }
        try {
            JsonNode node = json.readTree(body);
            JsonNode message = node.at("/error/message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignore) {
            // 非 JSON 响应体，直接截断返回
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static String readBodySilently(InputStream body) {
        try (body) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static ProviderException toProviderException(Throwable t) {
        Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        if (cause instanceof ProviderException pe) {
            return pe;
        }
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return new ProviderException(ProviderException.Category.NETWORK, "请求超时：" + cause.getMessage(), cause);
        }
        if (cause instanceof java.net.ConnectException) {
            return new ProviderException(ProviderException.Category.NETWORK,
                    "无法连接服务端（请检查 base_url 与网络）：" + cause.getMessage(), cause);
        }
        return new ProviderException(ProviderException.Category.NETWORK,
                "网络错误：" + cause.getMessage(), cause);
    }

    private static CompletableFuture<Void> fail(StreamEventListener listener, ProviderException error) {
        listener.onEvent(new StreamEvent.Error(error));
        return CompletableFuture.failedFuture(error);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 供 ProviderFactory 使用的默认构造：共享传入的 HttpClient。 */
    public static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }
}
