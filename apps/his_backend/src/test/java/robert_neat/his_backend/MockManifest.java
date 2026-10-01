package robert_neat.his_backend;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Liczebnosci wierszy per tabela wygenerowane razem z migracjami
 * (apps/his_frontend/scripts/export-mocks -> src/test/resources/db/mock-manifest.json).
 */
record MockManifest(Map<String, Integer> reference, Map<String, Integer> mock) {

    static MockManifest load() {
        try (InputStream in = new ClassPathResource("db/mock-manifest.json").getInputStream()) {
            JsonNode root = JsonMapper.builder().build().readTree(in);
            return new MockManifest(counts(root.get("reference")), counts(root.get("mock")));
        } catch (IOException e) {
            throw new IllegalStateException("Brak db/mock-manifest.json - uruchom `pnpm export:mocks`", e);
        }
    }

    private static Map<String, Integer> counts(JsonNode node) {
        Map<String, Integer> result = new LinkedHashMap<>();
        node.properties().forEach(e -> result.put(e.getKey(), e.getValue().asInt()));
        return result;
    }
}
