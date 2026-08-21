package art.limitium.sofa.imports;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a library publishes about the code it generated, so consumers can reference those artifacts
 * instead of generating their own copies.
 * <p>
 * A consumer can rebuild the record graph on its own by parsing the same {@code .avsc} files, but it
 * cannot derive the names the producing module assigned - those depend on the producer's generator
 * definitions. The manifest carries exactly that missing piece, keyed by generator path so a
 * consumer's {@code pojo_common} asks the producer's {@code pojo_common}.
 * <p>
 * {@link #generators} is what separates "the producer never ran this generator" from "the producer
 * ran it and deliberately emitted nothing for this record". The first is fine and the consumer
 * generates locally; the second means the roles disagree between modules and must fail.
 */
public class Manifest {
    /** Maven style {@code groupId:artifactId} of the publishing library */
    public String artifact;

    /** Schema resource paths the publishing library loaded, relative to the {@code sofa/} resource root */
    public List<String> schemas = List.of();

    /** Generator paths the publishing library ran */
    public Set<String> generators = new LinkedHashSet<>();

    /** Avro record fullname to generator path to the artifact that generator produced */
    public Map<String, Map<String, Artifact>> records = new LinkedHashMap<>();

    /**
     * The identity a single generator assigned to a single record
     */
    public static class Artifact {
        public String namespace;
        public String name;
        public String fullname;

        public Artifact() {
        }

        public Artifact(String namespace, String name, String fullname) {
            this.namespace = namespace;
            this.name = name;
            this.fullname = fullname;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Builds the classpath resource path a manifest for the given coordinate is published at
     *
     * @param artifact Maven style {@code groupId:artifactId}
     * @return Resource path such as {@code sofa/manifests/com.example.car-lib.json}
     */
    public static String resourcePath(String artifact) {
        String[] parts = artifact.split(":");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new RuntimeException("Import `" + artifact + "` is not a `groupId:artifactId` coordinate");
        }
        return "sofa/manifests/" + parts[0] + "." + parts[1] + ".json";
    }

    /**
     * Reads a manifest from an input stream
     */
    public static Manifest read(InputStream is) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Manifest manifest = GSON.fromJson(reader, Manifest.class);
            if (manifest == null) {
                throw new IOException("Manifest is empty");
            }
            if (manifest.schemas == null) {
                manifest.schemas = List.of();
            }
            if (manifest.generators == null) {
                manifest.generators = new LinkedHashSet<>();
            }
            if (manifest.records == null) {
                manifest.records = new LinkedHashMap<>();
            }
            return manifest;
        }
    }

    /**
     * Writes this manifest to a file, creating parent folders as needed
     */
    public void write(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write manifest to " + path, e);
        }
    }

    /**
     * Looks up what a generator produced for a record
     *
     * @param recordFullname Avro fullname of the record
     * @param generatorPath  Generator path to look under
     * @return The artifact, or null when this generator produced nothing for that record
     */
    public Artifact artifactFor(String recordFullname, String generatorPath) {
        Map<String, Artifact> byGenerator = records.get(recordFullname);
        return byGenerator == null ? null : byGenerator.get(generatorPath);
    }
}
