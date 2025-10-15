package org.learningjava.bmtool1.infrastructure.adapter.out.fs;

import org.learningjava.bmtool1.application.port.PairReaderPort;
import org.learningjava.bmtool1.domain.model.PairId;
import org.learningjava.bmtool1.domain.model.SourcePair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FileSystemPairReader implements PairReaderPort {
    @Override
    public List<SourcePair> discoverPairs(String rootDir) {
        try {
            Map<String, Path> plsql = new HashMap<>();
            Map<String, Path> java = new HashMap<>();

            try (var s = Files.walk(Path.of(rootDir))) {
                s.filter(Files::isRegularFile).forEach(p -> {
                    var name = p.getFileName().toString();
                    if (name.endsWith(".sql") || name.endsWith(".plsql") || name.endsWith(".pkb") || name.endsWith(".pks")) {
                        plsql.put(stripExt(name), p);
                    } else if (name.endsWith(".java")) {
                        java.put(stripExt(name), p);
                    }
                });
            }

            List<SourcePair> out = new ArrayList<>();
            for (var key : plsql.keySet()) {
                if (java.containsKey(key)) {
                    SourcePair pair = new SourcePair(
                            PairId.newId(),
                            plsql.get(key).toString(),
                            java.get(key).toString()
                    );
                    out.add(pair);

                    // 👇 Print the pair for debugging
                    System.out.printf("Discovered pair: SQL=%s  JAVA=%s%n",
                            pair.plsqlPath(), pair.javaPath());
                }
            }

            if (out.isEmpty()) {
                System.out.println("⚠️ No pairs found under: " + rootDir);
            } else {
                System.out.println("✅ Total pairs found: " + out.size());
            }

            return out;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String readFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
