package org.learningjava.bmtool1.application.usecase;

import org.learningjava.bmtool1.domain.model.Block;
import org.learningjava.bmtool1.domain.model.PlsqlSnippet;
import org.learningjava.bmtool1.domain.service.ASTParser.PlsqlBlockExtractor;
import org.learningjava.bmtool1.domain.service.EventCodeUtil;
import org.learningjava.bmtool1.application.port.SnippetRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ExtractSnippetsUseCase {

    private final PlsqlBlockExtractor extractor;
    private final SnippetRepository repo;

    public ExtractSnippetsUseCase(PlsqlBlockExtractor extractor,
                                  SnippetRepository repo) {
        this.extractor = extractor;
        this.repo = repo;
    }

    public void extract(Path plsqlFile) throws Exception {
        List<Block> blocks = extractor.extract(plsqlFile);

        String eventCode = EventCodeUtil.buildEventCodeFromBlocks(blocks);
        String domain = EventCodeUtil.mapInputTypeFromEventCode(eventCode, "Unknown");

        for (Block block : blocks) {
            PlsqlSnippet snippet = new PlsqlSnippet(
                    eventCode,
                    domain,
                    block.type(),
                    block.text(),
                    null // no translation yet
            );
            repo.savePending(snippet);
        }

        System.out.println("✅ Extracted " + blocks.size() + " snippets from " + plsqlFile);
    }
}
