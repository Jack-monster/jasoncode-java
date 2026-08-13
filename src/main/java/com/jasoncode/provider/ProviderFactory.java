package com.jasoncode.provider;

import com.jasoncode.config.ProviderConfig;
import com.jasoncode.provider.anthropic.AnthropicProvider;
import com.jasoncode.provider.openai.OpenAiProvider;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 按 protocol 分发创建 ChatProvider 实例。
 * <p>
 * 进程内共享一个 HttpClient（连接复用）与一个虚拟线程执行器（流读取）。
 * 新增协议只需在此处增加一个分支与一个实现类。
 */
public final class ProviderFactory {

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private static final Executor SHARED_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private ProviderFactory() {
    }

    public static ChatProvider create(ProviderConfig config) {
        return switch (config.protocol()) {
            case OPENAI -> new OpenAiProvider(config, SHARED_HTTP_CLIENT, SHARED_EXECUTOR);
            case ANTHROPIC -> new AnthropicProvider(config, SHARED_HTTP_CLIENT, SHARED_EXECUTOR);
        };
    }
}
