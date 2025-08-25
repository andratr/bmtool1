package org.learningjava.bmtool1.application.port;
import java.util.List;
public interface EmbeddingPort {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}