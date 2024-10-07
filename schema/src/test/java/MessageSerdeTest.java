import com.example.avro.common.pojo.NestedNestedRecord;
import com.example.avro.common.pojo.NestedRecord;
import com.example.avro.common.pojo.Suit3;
import com.example.avro5.messages.pojo.Root5;
import com.example.avro5.messages.serde.Root5Serde;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class MessageSerdeTest {
    @Test
    public void test() {
        Root5 root = new Root5();


        root.longFieldPK = 12L;
        NestedRecord nestedRecord1 = new NestedRecord();
        nestedRecord1.nenumField = Suit3.CLUBS;
        nestedRecord1.stringField = "qwe";
        nestedRecord1.nnestedField = new NestedNestedRecord();
        nestedRecord1.nnestedField.stringField = "asdf";

        NestedRecord nestedRecord2 = new NestedRecord();
        nestedRecord2.nenumField = Suit3.HEARTS;
        nestedRecord2.stringField = "fgasfg";
        nestedRecord2.nnestedField = new NestedNestedRecord();
        nestedRecord2.nnestedField.stringField = "ssss";

        root.arrOfArr = Arrays.asList(nestedRecord1, nestedRecord2);


        Root5Serde root5Serde = new Root5Serde();

        byte[] serialize = root5Serde.serializer().serialize(null, root);

        Root5 deserialize = root5Serde.deserializer().deserialize(null, serialize);

        System.out.println(deserialize);
    }
}
