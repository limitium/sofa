package art.limitium.sofa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorTest {
    @TempDir
    Path tempDir;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Test
    void shouldGenerateAllTemplateTypes() throws IOException {
        // Given
        String configPath = copyTestResources("test-config.yaml", "schemas", "templates");
        
        // When
        Factory.main(new String[]{configPath});
        
        // Then
        verifyGeneratedFiles(
            "Address.json",
                "Cart.json",
                "CartItem.json",
                "CustomerInfo.json",
                "Order.json",
                "OrderItem.json",
                "OrderStatus.json",
                "Product.json"
        );
    }
    
    private String copyTestResources(String configFile, String... directories) throws IOException {
        // Copy config file
        String configContent = readResource("/generator-test/" + configFile);
        Path configPath = tempDir.resolve(configFile);
        Files.writeString(configPath, configContent);
        
        // Copy directories
        for (String dir : directories) {
            copyDirectory("/generator-test/" + dir, tempDir.resolve(dir));
        }
        
        return configPath.toString();
    }
    
    private void verifyGeneratedFiles(String... fileNames) throws IOException {
        Path outputDir = tempDir.resolve("generated");
        
        for (String fileName : fileNames) {
            Path generatedFile = outputDir.resolve(fileName);
            Path expectedFile = Path.of("src/test/resources/generator-test/expected/" + fileName);
            
            assertTrue(Files.exists(generatedFile), "Generated file not found: " + fileName);
            
            JsonNode generated = parseAndFormat(generatedFile);
            JsonNode expected = parseAndFormat(expectedFile);
            
            assertEquals(
                expected,
                generated,
                "Generated file doesn't match expected for: " + fileName
            );
        }
    }
    
    private JsonNode parseAndFormat(Path jsonFile) throws IOException {
        return objectMapper.readTree(Files.readString(jsonFile));
    }
    
    private String readResource(String path) throws IOException {
        return new String(getClass().getResourceAsStream(path).readAllBytes());
    }
    
    private void copyDirectory(String resourcePath, Path targetPath) throws IOException {
        Files.createDirectories(targetPath);
        
        // List resources in the directory
        List<String> resources = ResourceUtils.listResources(resourcePath);
        
        for (String resource : resources) {
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            String content = readResource(resource);
            Files.writeString(targetPath.resolve(fileName), content);
        }
    }
}