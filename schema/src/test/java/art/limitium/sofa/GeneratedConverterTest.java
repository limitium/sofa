package art.limitium.sofa;

import com.example.avro.entities.converter.NestedRecordConverter;
import com.example.avro.entities.pojo.NestedRecord;
import com.example.avro.entities.serde.NestedRecordSerde;
import com.example.avro4.entities.converter.NestedRecord4Converter;
import com.example.avro4.entities.pojo.NestedRecord4;
import com.example.avro4.entities.serde.NestedRecord4Serde;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Round trips generated dependents through their Connect converter, checking the owner link
 * survives the Struct the way it would through a JDBC sink and source.
 */
class GeneratedConverterTest {

    @Test
    void polymorphicOwnerSurvivesConnect() {
        NestedRecordSerde serde = new NestedRecordSerde();
        NestedRecordConverter converter = new NestedRecordConverter();
        NestedRecord pojo = new NestedRecord();
        pojo.stringField = "s";
        pojo.ownerEntity = "Root";
        pojo.ownerId = 42;

        SchemaAndValue struct = converter.toConnectData("t", serde.serializer().serialize("t", pojo));
        NestedRecord back = serde.deserializer().deserialize("t",
                converter.fromConnectData("t", struct.schema(), struct.value()));

        assertEquals("s", back.stringField);
        assertEquals("Root", back.ownerEntity);
        assertEquals(42, back.ownerId);
    }

    @Test
    void singleOwnerSurvivesConnect() {
        NestedRecord4Serde serde = new NestedRecord4Serde();
        NestedRecord4Converter converter = new NestedRecord4Converter();
        NestedRecord4 pojo = new NestedRecord4();
        pojo.stringField = "s";
        pojo.root4Id = 42;

        SchemaAndValue struct = converter.toConnectData("t", serde.serializer().serialize("t", pojo));
        NestedRecord4 back = serde.deserializer().deserialize("t",
                converter.fromConnectData("t", struct.schema(), struct.value()));

        assertEquals("s", back.stringField);
        assertEquals(42, back.root4Id);
    }

    @Test
    void nullOwnerEntitySerializes() {
        NestedRecordSerde serde = new NestedRecordSerde();
        NestedRecord pojo = new NestedRecord();
        pojo.ownerId = 42;

        NestedRecord back = serde.deserializer().deserialize("t", serde.serializer().serialize("t", pojo));

        assertNull(back.ownerEntity);
        assertEquals(42, back.ownerId);
    }
}
