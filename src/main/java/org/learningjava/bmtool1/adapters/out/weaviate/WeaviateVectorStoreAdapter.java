package org.learningjava.bmtool1.adapters.out.weaviate;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.model.RetrievalResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaviateVectorStoreAdapter implements VectorStorePort {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;
    private final String className;

    public WeaviateVectorStoreAdapter(String baseUrl, String apiKey, String className) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.className = className;
    }

    @Override
    public void ensureSchema() {
        try {
            var existing = request("GET", "/v1/schema/" + className, null, true);
            if (existing != null && existing.has("class")) return;
        } catch (RuntimeException re) {
            var c = re.getCause();
            if (!(c instanceof IOException) || !c.getMessage().contains("404")) {
                throw re;
            }
        }
        createSchema();
    }

    private void createSchema() {
        ObjectNode cls = om.createObjectNode();
        cls.put("class", className);
        cls.put("vectorizer", "none");

        ArrayNode props = om.createArrayNode();
        props.add(prop("pairId", "text"));
        props.add(prop("plsqlSnippet", "text"));
        props.add(prop("javaSnippet", "text"));
        props.add(prop("plsqlType", "text"));
        props.add(prop("javaType", "text"));
        props.add(prop("javaHelpers", "text[]")); // ✅ new property for helpers
        cls.set("properties", props);

        request("POST", "/v1/schema", cls, false);
    }

    @Override
    public void upsertMappings(List<BlockMapping> mappings, List<float[]> vectors) {
        if (mappings.size() != vectors.size()) {
            throw new IllegalArgumentException("mappings and vectors must have the same size");
        }

        ArrayNode objects = om.createArrayNode();
        for (int i = 0; i < mappings.size(); i++) {
            BlockMapping m = mappings.get(i);

            ObjectNode obj = om.createObjectNode();
            obj.put("class", className);

            ObjectNode props = om.createObjectNode();
            props.put("pairId", m.pairId());
            props.put("plsqlSnippet", m.plsqlSnippet());
            props.put("javaSnippet", m.javaSnippet());
            props.put("plsqlType", m.plsqlType());
            props.put("javaType", m.javaType());

            // ✅ add helpers if present
            if (m.javaHelpers() != null && !m.javaHelpers().isEmpty()) {
                ArrayNode helpersArr = om.createArrayNode();
                for (String h : m.javaHelpers()) {
                    helpersArr.add(h);
                }
                props.set("javaHelpers", helpersArr);
            }

            obj.set("properties", props);

            // stable id for deterministic upserts
            String composite = m.pairId() + "|" + m.plsqlType() + "|" + m.javaType();
            String stableId = UUID.nameUUIDFromBytes(composite.getBytes(StandardCharsets.UTF_8)).toString();
            obj.put("id", stableId);

            obj.set("vector", floatArray(vectors.get(i)));

            objects.add(obj);
        }

        ObjectNode body = om.createObjectNode();
        body.set("objects", objects);
        request("POST", "/v1/batch/objects", body, false);
    }

    @Override
    public List<RetrievalResult> query(String query, float[] queryVec, int k) {
        String vectorJson = toJsonArray(queryVec);

        String gqlQuery = """
            {
              Get {
                %s(nearVector: {vector: %s}, limit: %d) {
                  pairId
                  plsqlSnippet
                  javaSnippet
                  plsqlType
                  javaType
                  javaHelpers
                  _additional { distance }
                }
              }
            }
            """.formatted(className, vectorJson, k);

        ObjectNode gqlBody = om.createObjectNode();
        gqlBody.put("query", gqlQuery);

        JsonNode gql = request("POST", "/v1/graphql", gqlBody, false);

        List<RetrievalResult> out = new ArrayList<>();
        JsonNode arr = gql.path("data").path("Get").path(className);
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                BlockMapping mapping = new BlockMapping(
                        node.path("pairId").asText(),
                        node.path("plsqlSnippet").asText(),
                        node.path("javaSnippet").asText(),
                        node.path("plsqlType").asText(),
                        node.path("javaType").asText(),
                        readHelpers(node.path("javaHelpers"))
                );
                double score = 1.0 - node.path("_additional").path("distance").asDouble(0.0);
                out.add(new RetrievalResult(mapping, score));
            }
        }
        return out;
    }

    // ---------- helpers ----------

    private ObjectNode prop(String name, String dataType) {
        ObjectNode p = om.createObjectNode();
        p.put("name", name);
        ArrayNode dt = om.createArrayNode();
        dt.add(dataType);
        p.set("dataType", dt);
        return p;
    }

    private ArrayNode floatArray(float[] v) {
        ArrayNode a = om.createArrayNode();
        for (float f : v) a.add(f);
        return a;
    }

    private String toJsonArray(float[] v) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Double.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private List<String> readHelpers(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) out.add(n.asText());
        }
        return out;
    }

    private JsonNode request(String method, String path, Object body, boolean ignoreConflict) {
        try {
            Request.Builder b = new Request.Builder().url(baseUrl + path);
            if (apiKey != null && !apiKey.isBlank()) {
                b.addHeader("Authorization", "Bearer " + apiKey);
            }
            if (body != null) {
                byte[] json = om.writeValueAsBytes(body);
                b.method(method, RequestBody.create(json, JSON));
            } else {
                b.method(method, null);
            }

            try (Response resp = http.newCall(b.build()).execute()) {
                String respBody = resp.body() != null ? resp.body().string() : "";
                if (ignoreConflict && resp.code() == 409) {
                    return om.createObjectNode().put("ok", true);
                }
                if (!resp.isSuccessful()) {
                    throw new IOException("Weaviate " + method + " " + path + " failed: " + resp.code()
                            + " body=" + respBody);
                }
                return respBody.isEmpty() ? om.createObjectNode() : om.readTree(respBody);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
