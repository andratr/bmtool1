package org.learningjava.bmtool1.application.port;

import org.learningjava.bmtool1.domain.model.pairs.SourcePair;

import java.util.List;

public interface PairReaderPort {
    List<SourcePair> discoverPairs(String rootDir); // e.g., pairs by name: foo.sql ↔ foo.java

    String readFile(String path);
}
