//package org.learningjava.bmtool1.infrastructure.adapter.in.web.debugControllers;
//
//import org.learningjava.bmtool1.domain.model.Block;
//import org.learningjava.bmtool1.domain.model.BlockMapping;
//import org.learningjava.bmtool1.infrastructure.adapter.out.blockASTParser.JavaBlockExtractor;
//import org.learningjava.bmtool1.infrastructure.adapter.out.blockASTParser.PlsqlBlockExtractorAdapter;
//import org.learningjava.bmtool1.domain.service.ingest.BlockMapper;
//import org.learningjava.bmtool1.infrastructure.adapter.out.fs.FileSystemPairReader;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//
//@RestController
//@RequestMapping("/debug/mappings")
//public class DebugMappingController {
//    private static final Logger log = LoggerFactory.getLogger(DebugMappingController.class);
//
//    private final PlsqlBlockExtractorAdapter plsqlExtractor = new PlsqlBlockExtractorAdapter();
//    private final JavaBlockExtractor javaExtractor = new JavaBlockExtractor();
//    private final BlockMapper mapper = new BlockMapper();
//    private final FileSystemPairReader pairReader = new FileSystemPairReader();
//
//    @GetMapping
//    public List<BlockMapping> processFolder(@RequestParam String folder) throws Exception {
//        List<BlockMapping> allMappings = new ArrayList<>();
//
//
//        var pairs = pairReader.discoverPairs(folder);
//
//        for (var pair : pairs) {
//            Path plsqlPath = Path.of(pair.plsqlPath());
//            Path javaPath = Path.of(pair.javaPath());
//
//            log.info("Processing pair: {} <-> {}", plsqlPath, javaPath);
//
//            List<Block> plsqlBlocks = plsqlExtractor.extract(plsqlPath);
//            List<Block> javaBlocks = javaExtractor.extract(javaPath);
//
//            List<BlockMapping> mappings = mapper.map(plsqlBlocks, javaBlocks);
//            allMappings.addAll(mappings);
//        }
//
//        log.info("Finished processing folder: {} ({} mappings)", folder, allMappings.size());
//        return allMappings;
//    }
//}
