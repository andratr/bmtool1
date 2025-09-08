package org.learningjava.bmtool1.application.port;

public interface ChatLLMPort {
    String id();  // "ollama", "openrouter", etc.
    String chat(String prompt, String model);
}
