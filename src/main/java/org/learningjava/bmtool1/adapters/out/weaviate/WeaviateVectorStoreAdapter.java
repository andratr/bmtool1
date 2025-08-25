package org.learningjava.bmtool1.adapters.out.weaviate;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import org.learningjava.bmtool1.application.port.VectorStorePort;
import org.learningjava.bmtool1.domain.model.BlockMapping;
import org.learningjava.bmtool1.domain.model.RetrievalResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        // Strict: schema must match weaviate.schema.json exactly (for class, vectorizer, properties).
        // If class is missing -> create. If any difference -> delete and recreate.
        JsonNode desiredClass = loadDesiredClassFromFileStrict(); // throws if missing

        JsonNode live = getClassIfExists(className);
        if (live == null) {
            request("POST", "/v1/schema", desiredClass, false);
            return;
        }

        if (!equalNormalized(desiredClass, live)) {
            // destructive replace
            request("DELETE", "/v1/schema/" + className, null, false);
            request("POST", "/v1/schema", desiredClass, false);
        }
    }

    /** Strictly load desired class for this.className from ./weaviate.schema.json; error if file or class is missing. */
    private JsonNode loadDesiredClassFromFileStrict() {
        try {
            Path path = Paths.get("weaviate.schema.json"); // repo root
            if (!Files.exists(path)) {
                throw new IllegalStateException("No JSON shema file");
            }

            JsonNode root = om.readTree(Files.readAllBytes(path));

            // Allow either { "classes": [...] } or a single-class object
            if (root.has("classes") && root.get("classes").isArray()) {
                for (JsonNode c : root.get("classes")) {
                    if (className.equals(c.path("class").asText())) return c;
                }
                throw new IllegalStateException("Class '" + className + "' not found in weaviate.schema.json");
            } else if (className.equals(root.path("class").asText())) {
                return root; // single-class schema file
            } else {
                throw new IllegalStateException("Class '" + className + "' not found in weaviate.schema.json");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** GET /v1/schema/{class} and return node, or null on 404. */
    private JsonNode getClassIfExists(String cname) {
        try {
            return request("GET", "/v1/schema/" + cname, null, false);
        } catch (RuntimeException re) {
            Throwable c = re.getCause();
            if (c instanceof IOException && c.getMessage() != null && c.getMessage().contains(" 404 ")) {
                return null;
            }
            throw re;
        }
    }

    /** Compare relevant bits only (class, vectorizer, properties name+type), order-insensitive. */
    private boolean equalNormalized(JsonNode desired, JsonNode live) {
        ObjectNode dn = normalizeClass(desired);
        ObjectNode ln = normalizeClass(live);
        return dn.equals(ln);
    }

    private ObjectNode normalizeClass(JsonNode c) {
        ObjectNode out = om.createObjectNode();
        out.put("class", c.path("class").asText());
        out.put("vectorizer", c.path("vectorizer").asText("none"));

        Map<String, List<String>> props = new TreeMap<>();
        JsonNode arr = c.path("properties");
        if (arr.isArray()) {
            for (JsonNode p : arr) {
                String name = p.path("name").asText();
                List<String> types = new ArrayList<>();
                JsonNode dt = p.path("dataType");
                if (dt.isArray()) for (JsonNode t : dt) types.add(t.asText());
                props.put(name, types);
            }
        }
        ArrayNode propsArr = om.createArrayNode();
        for (var e : props.entrySet()) {
            ObjectNode pn = om.createObjectNode();
            pn.put("name", e.getKey());
            ArrayNode dts = om.createArrayNode();
            for (String t : e.getValue()) dts.add(t);
            pn.set("dataType", dts);
            propsArr.add(pn);
        }
        out.set("properties", propsArr);
        return out;
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

            if (m.javaHelpers() != null && !m.javaHelpers().isEmpty()) {
                ArrayNode helpersArr = om.createArrayNode();
                for (String h : m.javaHelpers()) helpersArr.add(h);
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
