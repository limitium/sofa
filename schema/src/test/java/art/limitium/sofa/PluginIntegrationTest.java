package art.limitium.sofa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that SofaPlugin-based filters and type converters are discovered and applied during
 * generation.
 */
class PluginIntegrationTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Test
    void shouldApplyCustomFilterAndTypeConverterFromPlugin() throws IOException {
        // Given
        String configPath = copyTestResources("generator-plugin-test/test-config.yaml", "generator-plugin-test/schemas", "generator-plugin-test/templates", "generator-plugin-test/expected");

        // When
        Factory.main(new String[] {configPath});

        // Then
        Path output = tempDir.resolve("generated-plugin").resolve("Simple-plugin.json");
        assertTrue(Files.exists(output), "Generated plugin file not found");

        JsonNode generated = parseAndFormat(output);
        JsonNode expected =
                parseAndFormatFromResource("/generator-plugin-test/expected/Simple-plugin.json");

        assertEquals(expected, generated);
    }

    private String copyTestResources(String configFile, String... directories) throws IOException {
        String configContent = readResource("/" + configFile);
        Path configPath = tempDir.resolve(new File(configFile).getName());
        Files.writeString(configPath, configContent);

        for (String dir : directories) {
            copyDirectory("/" + dir, tempDir.resolve(new File(dir).getName()));
        }

        return configPath.toString();
    }

    private JsonNode parseAndFormat(Path jsonFile) throws IOException {
        return objectMapper.readTree(Files.readString(jsonFile));
    }

    private JsonNode parseAndFormatFromResource(String resourcePath) throws IOException {
        return objectMapper.readTree(readResource(resourcePath));
    }

    private String readResource(String path) throws IOException {
        return new String(getClass().getResourceAsStream(path).readAllBytes());
    }

    private void copyDirectory(String resourcePath, Path targetPath) throws IOException {
        Files.createDirectories(targetPath);
        for (String resource : ResourceUtils.listResources(resourcePath)) {
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            String content = readResource(resource);
            Files.writeString(targetPath.resolve(fileName), content);
        }
    }
}

