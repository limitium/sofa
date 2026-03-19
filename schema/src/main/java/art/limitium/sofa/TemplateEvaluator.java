package art.limitium.sofa;

import com.mitchellbosecke.pebble.template.PebbleTemplate;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

interface TemplateEvaluator {
    String evaluateToString(PebbleTemplate template, Map<String, ?> context);

    static TemplateEvaluator defaultEvaluator() {
        return (template, context) -> {
            StringWriter stringWriter = new StringWriter();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) context;
                template.evaluate(stringWriter, ctx);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return stringWriter.toString();
        };
    }
}

