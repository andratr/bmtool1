package org.learningjava.bmtool1.application.port;

public interface ChatLLMPort {
    String provider();

    String chat(String prompt, String model);
}
