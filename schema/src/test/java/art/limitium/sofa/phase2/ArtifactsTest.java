package art.limitium.sofa.phase2;

import com.example.avro.entities.builder.RootBuilder;
import com.example.avro.entities.fb.FbRoot;
import com.example.avro.entities.pojo.Root;
import com.example.avro2.entities.builder.Root3Builder;
import com.example.avro2.entities.fb.FbRoot3;
import com.example.avro2.entities.pojo.Root3;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ArtifactsTest {
    @Test
    void testRoot3Conversion() {
        // Generate a fully populated Root3 instance using Instancio
        Root3 originalRoot3 = Instancio.of(Root3.class)
                .generate(field(Root3::getArrayField), gen -> gen.collection().size(3))
                .create();

        // Convert the Root3 instance to FlatBuffer
        FbRoot3 flatBuffer = Root3Builder.buildFlatbufferFrom(originalRoot3);

        // Convert back to Root3 POJO
        Root3 convertedRoot3 = Root3Builder.buildPojoFrom(flatBuffer);

        // Assertions to verify the conversion
        assertEquals(originalRoot3.getStringField(), convertedRoot3.getStringField());
        assertEquals(originalRoot3.getIntField(), convertedRoot3.getIntField());
        assertEquals(originalRoot3.getLongField(), convertedRoot3.getLongField());
        assertEquals(originalRoot3.getTimeField(), convertedRoot3.getTimeField());
        assertEquals(originalRoot3.getDateField(), convertedRoot3.getDateField());
        assertEquals(originalRoot3.getFloatField(), convertedRoot3.getFloatField());
        assertEquals(originalRoot3.getDoubleField(), convertedRoot3.getDoubleField());
        assertEquals(originalRoot3.getBooleanField(), convertedRoot3.getBooleanField());
        assertArrayEquals(originalRoot3.getBytesField(), convertedRoot3.getBytesField());
        assertEquals(originalRoot3.getArrayField(), convertedRoot3.getArrayField());
        assertEquals(originalRoot3.getEnumField(), convertedRoot3.getEnumField());
        assertEquals(originalRoot3.getNested33Field().getStringField(), convertedRoot3.getNested33Field().getStringField());
        assertEquals(originalRoot3.getNested33Field().getNnested33Field().getStringField(), convertedRoot3.getNested33Field().getNnested33Field().getStringField());
        assertEquals(originalRoot3.getNested333Field().getLongField(), convertedRoot3.getNested333Field().getLongField());
        assertEquals(originalRoot3.getNested333Field().getNnestedField().getStringField(), convertedRoot3.getNested333Field().getNnestedField().getStringField());


    }

    @Test
    void testRoot3NullableConversion() {
        // Generate a fully populated Root3 instance using Instancio
        Root3 originalRoot3 = Instancio.of(Root3.class)
                .generate(field(Root3::getArrayField), gen -> gen.collection().size(3))
                .create();

        originalRoot3.nested33Field.nnested33Field = null;
        originalRoot3.nested333Field = null;

        // Convert the Root3 instance to FlatBuffer
        FbRoot3 flatBuffer = Root3Builder.buildFlatbufferFrom(originalRoot3);

        // Convert back to Root3 POJO
        Root3 convertedRoot3 = Root3Builder.buildPojoFrom(flatBuffer);

        // Assertions to verify the conversion
        assertEquals(originalRoot3.getStringField(), convertedRoot3.getStringField());
        assertEquals(originalRoot3.getIntField(), convertedRoot3.getIntField());
        assertEquals(originalRoot3.getLongField(), convertedRoot3.getLongField());
        assertEquals(originalRoot3.getTimeField(), convertedRoot3.getTimeField());
        assertEquals(originalRoot3.getDateField(), convertedRoot3.getDateField());
        assertEquals(originalRoot3.getFloatField(), convertedRoot3.getFloatField());
        assertEquals(originalRoot3.getDoubleField(), convertedRoot3.getDoubleField());
        assertEquals(originalRoot3.getBooleanField(), convertedRoot3.getBooleanField());
        assertArrayEquals(originalRoot3.getBytesField(), convertedRoot3.getBytesField());
        assertEquals(originalRoot3.getArrayField(), convertedRoot3.getArrayField());
        assertEquals(originalRoot3.getEnumField(), convertedRoot3.getEnumField());
        assertEquals(originalRoot3.getNested33Field().getStringField(), convertedRoot3.getNested33Field().getStringField());
        assertEquals(originalRoot3.getNested33Field().getNnested33Field().getStringField(), convertedRoot3.getNested33Field().getNnested33Field().getStringField());
        assertEquals(originalRoot3.getNested333Field().getLongField(), convertedRoot3.getNested333Field().getLongField());
        assertEquals(originalRoot3.getNested333Field().getNnestedField().getStringField(), convertedRoot3.getNested333Field().getNnestedField().getStringField());


    }


    @Test
    public void testRootConversion() {
        // Generate a fully populated Root instance using Instancio
        Root originalRoot = Instancio.of(Root.class)
                .generate(field(Root::getArrayField), gen -> gen.collection().size(3))
                .create();

        // Convert the Root instance to FlatBuffer
        FbRoot flatBuffer = RootBuilder.buildFlatbufferFrom(originalRoot);

        // Convert back to Root POJO
        Root convertedRoot = RootBuilder.buildPojoFrom(flatBuffer);

        // Assertions to verify the conversion
        assertEquals(originalRoot.getStringField(), convertedRoot.getStringField());
        assertEquals(originalRoot.getIntField(), convertedRoot.getIntField());
        assertEquals(originalRoot.getLongField(), convertedRoot.getLongField());
        assertEquals(originalRoot.getTimeField(), convertedRoot.getTimeField());
        assertEquals(originalRoot.getDateField(), convertedRoot.getDateField());
        assertEquals(originalRoot.getFloatField(), convertedRoot.getFloatField());
        assertEquals(originalRoot.getDoubleField(), convertedRoot.getDoubleField());
        assertEquals(originalRoot.getBooleanField(), convertedRoot.getBooleanField());
        assertArrayEquals(originalRoot.getBytesField(), convertedRoot.getBytesField());
        assertEquals(originalRoot.getArrayField(), convertedRoot.getArrayField());
        assertEquals(originalRoot.getEnumField(), convertedRoot.getEnumField());
        assertEquals(originalRoot.getNestedField().getStringField(), convertedRoot.getNestedField().getStringField());
        assertEquals(originalRoot.getNested2Field().getStringField(), convertedRoot.getNested2Field().getStringField());
    }

}
