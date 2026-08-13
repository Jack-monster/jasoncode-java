package com.jasoncode;

import com.jasoncode.chat.Conversation;
import com.jasoncode.config.Protocol;
import com.jasoncode.config.ProviderConfig;
import com.jasoncode.history.InMemoryHistoryStore;
import com.jasoncode.provider.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainStatusLineTest {

    private final ProviderConfig provider = new ProviderConfig(
            "kimi", Protocol.OPENAI, "moonshot-v1-8k", "http://x", "sk-12345678", false);

    @Test
    void showsProviderModelTurnsAndContext() {
        Conversation conversation = new Conversation(new InMemoryHistoryStore());
        conversation.history().append(ChatMessage.user("你好"));
        conversation.history().append(ChatMessage.assistant("你好！"));

        String line = Main.statusLine(provider, conversation);

        assertEquals("kimi · moonshot-v1-8k (openai) │ 轮次: 1 │ 上下文: 5字符", line);
    }

    @Test
    void emptyConversationShowsZero() {
        Conversation conversation = new Conversation(new InMemoryHistoryStore());

        String line = Main.statusLine(provider, conversation);

        assertEquals("kimi · moonshot-v1-8k (openai) │ 轮次: 0 │ 上下文: 0字符", line);
    }

    @Test
    void largeContextUsesKiloFormat() {
        Conversation conversation = new Conversation(new InMemoryHistoryStore());
        conversation.history().append(ChatMessage.user("a".repeat(1500)));
        conversation.history().append(ChatMessage.assistant("b"));

        String line = Main.statusLine(provider, conversation);

        assertEquals("kimi · moonshot-v1-8k (openai) │ 轮次: 1 │ 上下文: 1.5k字符", line);
    }
}
