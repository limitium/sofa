package art.limitium.sofa;

import com.example.avro.common.pojo.Suit;
import com.example.avro4.messages.pojo.Root4;
import com.example.avro4.messages.serde.Root4Serde;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Round trips generated code through its serde, so the builder templates are checked by what they
 * emit rather than by what the template text looks like.
 */
class GeneratedSerdeTest {

    private final Root4Serde serde = new Root4Serde();

    private Root4 roundTrip(Root4 root) {
        byte[] bytes = serde.serializer().serialize("t", root);
        return serde.deserializer().deserialize("t", bytes);
    }

    @Test
    void nullListsStayNull() {
        Root4 root = new Root4();
        root.longFieldPK = 7;

        Root4 back = roundTrip(root);

        assertEquals(7, back.longFieldPK);
        assertNull(back.listEnums);
        assertNull(back.listNested);
        assertNull(back.listLinked);
    }

    @Test
    void emptyListsStayEmpty() {
        Root4 root = new Root4();
        root.listEnums = List.of();
        root.listNested = List.of();
        root.listLinked = List.of();

        Root4 back = roundTrip(root);

        assertEquals(List.of(), back.listEnums);
        assertEquals(List.of(), back.listNested);
        assertEquals(List.of(), back.listLinked);
    }

    @Test
    void listValuesSurvive() {
        Root4 root = new Root4();
        root.listEnums = List.of(Suit.values());

        assertEquals(List.of(Suit.values()), roundTrip(root).listEnums);
    }

    @Test
    void nullValueSerializesToNull() {
        assertNull(serde.serializer().serialize("t", null));
    }
}
