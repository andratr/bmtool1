package org.learningjava.bmtool1.application.port;

import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.model.RetrievalResult;
import java.util.List;

public interface VectorStorePort {
    void ensureSchema();
    void upsertMappings(List<BlockMapping> mappings, List<float[]> vectors);
    List<RetrievalResult> query(String query, float[] queryVec, int k);
}
