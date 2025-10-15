package org.learningjava.bmtool1.domain.service;

import org.learningjava.bmtool1.application.port.ChatLLMPort;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ChatRegistry {
    private final Map<String, ChatLLMPort> providers = new HashMap<>();

    public ChatRegistry(List<ChatLLMPort> adapters) {
        for (ChatLLMPort adapter : adapters) {
            providers.put(adapter.provider(), adapter);
        }
    }

    public ChatLLMPort get(String id) {
        return providers.get(id);
    }

    public Set<String> listProviders() {
        return providers.keySet();
    }
}
