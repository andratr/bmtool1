package org.learningjava.bmtool1.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class StartupTasks implements ApplicationRunner {

    private final VectorStorePort vectorStore;
    private final IngestPairsUseCase ingest;
    private final TaskExecutor executor;
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${schema.sync.enabled:true}") private boolean syncEnabled;
    @Value("${weaviate.url:http://weaviate:8080}") private String weaviateUrl;
    @Value("${weaviate.className:PairChunk}") private String className;
    @Value("${ingest.on-replace:true}") private boolean ingestOnReplace;
    @Value("${ingest.rootDir:}") private String ingestRootDir;

    public StartupTasks(VectorStorePort vectorStore,
                        IngestPairsUseCase ingest,
                        @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.vectorStore = vectorStore;
        this.ingest = ingest;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!syncEnabled) return;

        CompletableFuture
                .supplyAsync(this::schemaDiffersFromFile, executor)
                .thenAcceptAsync(changed -> {
                    if (changed) {
                        vectorStore.ensureSchema();  // drop & recreate
                        if (ingestOnReplace && !ingestRootDir.isBlank()) {
                            try {
                                ingest.ingestDirectory(ingestRootDir);
                            } catch (Exception e) {
                                throw new RuntimeException("Ingest failed: " + ingestRootDir, e);
                            }
                        }
                    }
                }, executor);
    }

    private boolean schemaDiffersFromFile() {
        try {
            Path json = Path.of("weaviate.schema.json");
            if (!Files.exists(json)) throw new IllegalStateException("No JSON shema file");
            JsonNode desired = om.readTree(Files.readAllBytes(json));
            JsonNode desiredClass = extractClass(desired, className);

            Request req = new Request.Builder()
                    .url(trim(weaviateUrl) + "/v1/schema/" + className)
                    .get().build();
            try (Response resp = http.newCall(req).execute()) {
                if (resp.code() == 404) return true; // class missing
                if (!resp.isSuccessful()) throw new RuntimeException("GET schema failed: " + resp.code());
                JsonNode live = om.readTree(resp.body().string());
                return !normalize(desiredClass).equals(normalize(live));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String trim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length()-1) : s;
    }

    private JsonNode extractClass(JsonNode root, String cls) {
        if (root.has("classes") && root.get("classes").isArray()) {
            for (JsonNode c : root.get("classes")) {
                if (cls.equals(c.path("class").asText())) return c;
            }
        } else if (cls.equals(root.path("class").asText())) {
            return root;
        }
        throw new IllegalStateException("Class '" + cls + "' not found in weaviate.schema.json");
    }

    private JsonNode normalize(JsonNode c) {
        var out = om.createObjectNode();
        out.put("class", c.path("class").asText());
        out.put("vectorizer", c.path("vectorizer").asText("none"));

        Map<String, List<String>> map = new TreeMap<>();
        var arr = c.path("properties");
        if (arr.isArray()) {
            for (JsonNode p : arr) {
                var name = p.path("name").asText();
                var types = new ArrayList<String>();
                var dt = p.path("dataType");
                if (dt.isArray()) dt.forEach(t -> types.add(t.asText()));
                map.put(name, types);
            }
        }
        var propsArr = om.createArrayNode();
        for (var e : map.entrySet()) {
            var pn = out.objectNode();
            pn.put("name", e.getKey());
            var dts = out.arrayNode(); e.getValue().forEach(dts::add);
            pn.set("dataType", dts);
            propsArr.add(pn);
        }
        out.set("properties", propsArr);
        return out;
    }
}
