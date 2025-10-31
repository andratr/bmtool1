package org.learningjava.bmtool1.application.port;

public interface ChatLLMPort {
    String provider();

    String chat(String prompt, String model);


    default ChatResult chatWithUsage(String prompt, String model) {
        // default: call legacy method, no usage available
        return new ChatResult(chat(prompt, model), null);
    }

    record Usage(Integer promptTokens, Integer completionTokens) {}
    record ChatResult(String text, Usage usage) {}

}
