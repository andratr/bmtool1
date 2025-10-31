package org.learningjava.bmtool1.application.port;

import org.learningjava.bmtool1.domain.model.pairs.Block;

import java.nio.file.Path;
import java.util.List;

public interface BlockExtractorPort {
    List<Block> extract(Path sourceFile) throws Exception;
}
