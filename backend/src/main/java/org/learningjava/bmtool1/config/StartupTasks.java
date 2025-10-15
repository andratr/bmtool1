//package org.learningjava.bmtool1.config;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
//import org.learningjava.bmtool1.application.port.VectorStorePort;
//import org.learningjava.bmtool1.application.usecase.IngestPairsUseCase;
//import org.learningjava.bmtool1.infrastructure.adapter.in.web.debugControllers.DebugMappingController;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.core.task.TaskExecutor;
//import org.springframework.stereotype.Component;
//
//import java.util.concurrent.CompletableFuture;
//
//@Component
//public class StartupTasks implements ApplicationRunner {
//    private static final Logger log = LoggerFactory.getLogger(DebugMappingController.class);
//
//    private final VectorStorePort vectorStore;
//    private final IngestPairsUseCase ingest;
//    private final TaskExecutor executor;
//    private final OkHttpClient http = new OkHttpClient.Builder()
//            .connectTimeout(java.time.Duration.ofSeconds(5))
//            .readTimeout(java.time.Duration.ofSeconds(5))
//            .writeTimeout(java.time.Duration.ofSeconds(5))
//            .build();
//    private final ObjectMapper om = new ObjectMapper();
//
//    @Value("${schema.sync.enabled:true}")
//    private boolean syncEnabled;
//    @Value("${weaviate.url:http://weaviate:8080}")
//    private String weaviateUrl;
//    @Value("${weaviate.className:PairChunk}")
//    private String className;
//    @Value("${ingest.on-replace:true}")
//    private boolean ingestOnReplace;
//    @Value("${ingest.rootDir:}")
//    private String ingestRootDir;
//
//    public StartupTasks(VectorStorePort vectorStore,
//                        IngestPairsUseCase ingest,
//                        @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
//        this.vectorStore = vectorStore;
//        this.ingest = ingest;
//        this.executor = executor;
//    }
//
//    private static String trim(String s) {
//        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
//    }
//
//    @Override
//    public void run(ApplicationArguments args) {
//        if (!syncEnabled) {
//            log.info("Schema sync disabled (schema.sync.enabled=false)");
//            return;
//        }
//
//        log.info("=== StartupTasks BEGIN ===");
//        log.info("Checking Weaviate schema for class {}", className);
//
//        CompletableFuture
//                .supplyAsync(this::schemaDiffersFromFile, executor)
//                .thenAcceptAsync(changed -> {
//                    if (changed) {
//                        log.warn("Schema differs → calling ensureSchema()");
//                        vectorStore.ensureSchema();
//                        if (ingestOnReplace && !ingestRootDir.isBlank()) {
//                            log.info("Triggering ingest for folder {}", ingestRootDir);
//                            try {
//                                ingest.ingestDirectory(ingestRootDir);
//                            } catch (Exception e) {
//                                log.error("Ingest failed: {}", ingestRootDir, e);
//                            }
//                        }
//                    } else {
//                        log.info("Schema is already in sync with file");
//                    }
//                    log.info("=== StartupTasks END ===");
//                }, executor);
//    }
//
//    private boolean schemaDiffersFromFile() {
//        final String resourceName = "weaviate.schema.json";
//        try (var in = Thread.currentThread()
//                .getContextClassLoader()
//                .getResourceAsStream(resourceName)) {
//            if (in == null) {
//                log.error("Schema resource not found on classpath: {}", resourceName);
//                throw new IllegalStateException("No JSON schema resource: " + resourceName);
//            }
//
//            JsonNode desiredRoot = om.readTree(in);
//            JsonNode desiredClass = extractClass(desiredRoot, className);
//
//            String url = trim(weaviateUrl) + "/v1/schema/" + className;
//            log.info("Fetching schema from {}", url);
//
//            Request req = new Request.Builder().url(url).get().build();
//            try (Response resp = http.newCall(req).execute()) {
//                if (resp.code() == 404) {
//                    log.warn("Weaviate returned 404 → schema missing");
//                    return true;
//                }
//                if (!resp.isSuccessful()) {
//                    throw new RuntimeException("GET schema failed: " + resp.code());
//                }
//                String raw = resp.body().string();
//                JsonNode live = om.readTree(raw);
//
//                boolean differs = !desiredClass.equals(live);
//                log.info("Schemas equal? {}", !differs);
//                return differs;
//            }
//        } catch (Exception e) {
//            log.error("Error while comparing schemas", e);
//            throw new RuntimeException(e);
//        }
//    }
//
//    private JsonNode extractClass(JsonNode root, String cls) {
//        if (root.has("classes") && root.get("classes").isArray()) {
//            for (JsonNode c : root.get("classes")) {
//                if (cls.equals(c.path("class").asText())) return c;
//            }
//        } else if (cls.equals(root.path("class").asText())) {
//            return root;
//        }
//        throw new IllegalStateException("Class '" + cls + "' not found in weaviate.schema.json");
//    }
//}
