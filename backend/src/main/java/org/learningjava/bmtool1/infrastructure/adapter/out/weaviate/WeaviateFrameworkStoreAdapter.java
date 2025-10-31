package org.learningjava.bmtool1.infrastructure.adapter.out.weaviate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.learningjava.bmtool1.application.port.FrameworkStorePort;
import org.learningjava.bmtool1.domain.model.framework.FrameworkSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WeaviateFrameworkStoreAdapter implements FrameworkStorePort {

    private static final Logger log = LoggerFactory.getLogger(WeaviateFrameworkStoreAdapter.class);
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    private final String baseUrl;     // e.g. http://localhost:8080
    private final String apiKey;      // optional
    private final String className;   // e.g. "FrameworkSnippet"

    public WeaviateFrameworkStoreAdapter(String baseUrl, String apiKey, String className) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.className = className;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    // ---------- PORT IMPLEMENTATION ----------

    @Override
    public void ensureSchema() {
        JsonNode desired = loadDesiredClassFromClasspath("weaviate.framework.schema.json", className);
        JsonNode live = getClassIfExists(className);
        if (live == null) {
            log.warn("Weaviate class '{}' missing → creating", className);
            request("POST", "/v1/schema", desired, false);
            return;
        }
        if (!normalizeClass(desired).equals(normalizeClass(live))) {
            log.warn("Weaviate class '{}' differs → dropping & recreating", className);
            request("DELETE", "/v1/schema/" + className, null, false);
            request("POST", "/v1/schema", desired, false);
        } else {
            log.info("Weaviate class '{}' is up to date", className);
        }
    }

    @Override
    public void upsertSymbols(List<FrameworkSymbol> symbols, List<float[]> vectors) {
        if (symbols == null || symbols.isEmpty()) {
            log.info("No framework symbols to upsert — skipping batch to avoid 422.");
            return;
        }
        if (symbols.size() != vectors.size()) {
            throw new IllegalArgumentException("symbols and vectors must have same size");
        }

        ArrayNode objects = om.createArrayNode();
        for (int i = 0; i < symbols.size(); i++) {
            FrameworkSymbol s = symbols.get(i);

            ObjectNode obj = om.createObjectNode();
            obj.put("class", className);

            // deterministic id from className#symbol
            String key = s.className() + "#" + s.symbol();
            String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            obj.put("id", id);

            ObjectNode props = om.createObjectNode();
            props.put("className", s.className());
            props.put("symbol", s.symbol());
            props.put("methodSignature", orEmpty(s.methodSignature()));
            props.put("snippet", orEmpty(s.snippet()));
            props.put("kind", orEmpty(s.kind()));

            ArrayNode tags = om.createArrayNode();
            if (s.tags() != null) s.tags().forEach(tags::add);
            props.set("tags", tags);

            obj.set("properties", props);
            obj.set("vector", floatArray(vectors.get(i)));

            objects.add(obj);
        }

        ObjectNode body = om.createObjectNode();
        body.set("objects", objects);
        request("POST", "/v1/batch/objects", body, false);
    }

    @Override
    public List<FrameworkSymbol> retrieve(String query, float[] vec, int k, List<String> mustHaveTags) {
        String gql = buildGraphQL(vec, k, mustHaveTags);
        ObjectNode body = om.createObjectNode().put("query", gql);

        JsonNode resp = request("POST", "/v1/graphql", body, false);
        List<FrameworkSymbol> out = new ArrayList<>();

        JsonNode arr = resp.path("data").path("Get").path(className);
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(new FrameworkSymbol(
                        n.path("className").asText(),
                        n.path("symbol").asText(),
                        n.path("methodSignature").asText(null),
                        n.path("snippet").asText(null),
                        n.path("kind").asText(null),
                        readTags(n.path("tags"))
                ));
            }
        }
        return out;
    }

    // ---------- INTERNALS ----------

    private String buildGraphQL(float[] vec, int k, List<String> tags) {
        String vectorJson = toJsonArray(vec);

        String where = "";
        if (tags != null && !tags.isEmpty()) {
            String values = String.join("\",\"", tags);
            where = """
                    where: {
                      path: ["tags"],
                      operator: ContainsAny,
                      valueTextArray: ["%s"]
                    },
                    """.formatted(values);
        }

        return """
                {
                  Get {
                    %s(
                      %s
                      nearVector: { vector: %s },
                      limit: %d
                    ) {
                      className
                      symbol
                      methodSignature
                      snippet
                      kind
                      tags
                      _additional { distance }
                    }
                  }
                }""".formatted(className, where, vectorJson, k);
    }

    private ArrayNode floatArray(float[] v) {
        ArrayNode a = om.createArrayNode();
        for (float f : v) a.add(f);
        return a;
    }

    private List<String> readTags(JsonNode node) {
        List<String> t = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(x -> t.add(x.asText()));
        }
        return t;
    }

    private String toJsonArray(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Double.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private JsonNode getClassIfExists(String cname) {
        try {
            return request("GET", "/v1/schema/" + cname, null, false);
        } catch (RuntimeException re) {
            var c = re.getCause();
            if (c instanceof IOException && c.getMessage() != null && c.getMessage().contains(" 404 ")) {
                return null;
            }
            throw re;
        }
    }

    private JsonNode loadDesiredClassFromClasspath(String resource, String expectedClassName) {
        try (var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Schema resource not found: " + resource);
            JsonNode root = om.readTree(in);
            if (root.has("classes") && root.get("classes").isArray()) {
                for (JsonNode c : root.get("classes")) {
                    if (expectedClassName.equals(c.path("class").asText())) return c;
                }
                throw new IllegalStateException("Class '" + expectedClassName + "' not found in " + resource);
            } else if (expectedClassName.equals(root.path("class").asText())) {
                return root;
            } else {
                throw new IllegalStateException("Class '" + expectedClassName + "' not found in " + resource);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
