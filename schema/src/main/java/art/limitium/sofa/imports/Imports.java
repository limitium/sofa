package art.limitium.sofa.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The manifests of every imported library, resolved into the lookups a {@link art.limitium.sofa.Generator}
 * needs.
 * <p>
 * Imports are all or nothing per library: importing a subset of a library's schemas would let records
 * lose owners or regain root-ness in the consumer, which is the drift the manifest exists to prevent.
 * So {@link #schemaSpecs()} force loads every schema the library declared.
 */
public class Imports {
    private static final Logger logger = LoggerFactory.getLogger(Imports.class);

    public static final Imports EMPTY = new Imports(List.of());

    private final List<Manifest> manifests;
    private final Set<String> generatorPaths = new LinkedHashSet<>();
    private final Set<String> recordFullnames = new LinkedHashSet<>();

    private Imports(List<Manifest> manifests) {
        this.manifests = manifests;
        for (Manifest manifest : manifests) {
            generatorPaths.addAll(manifest.generators);
            recordFullnames.addAll(manifest.records.keySet());
        }
    }

    /**
     * Loads the manifest of each imported coordinate from the classpath
     *
     * @param classLoader Class loader to resolve manifest resources with
     * @param coordinates List of {@code groupId:artifactId} coordinates
     * @return Resolved imports, or {@link #EMPTY} when nothing is imported
     */
    public static Imports load(ClassLoader classLoader, List<String> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return EMPTY;
        }

        List<Manifest> manifests = new ArrayList<>();
        for (String coordinate : coordinates) {
            String resourcePath = Manifest.resourcePath(coordinate);
            logger.info("Loading manifest for import `{}` from classpath resource `{}`", coordinate, resourcePath);
            try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new RuntimeException("Import `" + coordinate + "` publishes no manifest at `"
                            + resourcePath + "`; ensure the dependency is on the generator classpath and that it "
                            + "was built with a `manifest:` section in its configuration");
                }
                Manifest manifest = Manifest.read(is);
                logger.info("Import `{}` publishes {} records and {} schemas, from generators: {}",
                        coordinate, manifest.records.size(), manifest.schemas.size(),
                        String.join(", ", manifest.generators));
                manifest.records.forEach((record, byGenerator) -> byGenerator.forEach(
                        (generatorPath, artifact) -> logger.debug("  `{}` published `{}` as `{}`",
                                generatorPath, record, artifact.fullname)));
                manifests.add(manifest);
            } catch (IOException e) {
                throw new RuntimeException("Unable to read manifest for import `" + coordinate + "`", e);
            }
        }
        return new Imports(manifests);
    }

    public boolean isEmpty() {
        return manifests.isEmpty();
    }

    /**
     * Schema specs for every schema the imported libraries declared, in
     * {@code groupId:artifactId:path} form so they resolve from the classpath.
     * <p>
     * These are loaded ahead of the consumer's own schemas so referenced records parse first.
     */
    public List<String> schemaSpecs() {
        List<String> specs = new ArrayList<>();
        for (Manifest manifest : manifests) {
            for (String schema : manifest.schemas) {
                specs.add(manifest.artifact + ":" + schema);
            }
        }
        return specs;
    }

    /**
     * Checks whether any import declares this record
     */
    public boolean covers(String recordFullname) {
        return recordFullnames.contains(recordFullname);
    }

    /**
     * Checks whether any import ran this generator
     */
    public boolean ranGenerator(String generatorPath) {
        return generatorPaths.contains(generatorPath);
    }

    /**
     * Resolves what an imported library produced for a record under a generator path
     *
     * @return The published artifact, or null when no import published one
     */
    public Manifest.Artifact resolve(String recordFullname, String generatorPath) {
        for (Manifest manifest : manifests) {
            Manifest.Artifact artifact = manifest.artifactFor(recordFullname, generatorPath);
            if (artifact != null) {
                return artifact;
            }
        }
        return null;
    }

    /**
     * Names the libraries that declare a record, for error messages
     */
    public String publishersOf(String recordFullname) {
        return manifests.stream()
                .filter(m -> m.records.containsKey(recordFullname))
                .map(m -> m.artifact)
                .reduce((a, b) -> a + ", " + b)
                .orElse("<none>");
    }
}
