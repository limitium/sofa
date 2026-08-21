package art.limitium.sofa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorsTest {
    @TempDir
    Path tempDir;

    private URLClassLoader compiledClassLoader;

    @Test
    void shouldGenerateAllTemplateTypes() throws IOException, ClassNotFoundException {
        // Given
        String configPath = "src/main/resources/def.yaml";

        // When
        Factory.main(new String[]{configPath});

        // Compile and load generated Java files
        compileAndLoadGeneratedJavaFiles();

        // Then
        Class<?> aClass = compiledClassLoader.loadClass("com.example.avro5.entities.builder.Root5Builder");
        assertNotNull(aClass);
        System.out.println("Successfully loaded class: " + aClass.getName());
    }

    @Test
    void shouldTypePolymorphicOwnerColumnsToMatchTheGeneratedPojo() throws IOException {
        // Given the real schema set, where NestedRecord is reachable from more than one owner
        Factory.main(new String[]{"src/main/resources/def.yaml"});

        // When
        String pojo = Files.readString(Path.of(
                "build/generated/sources/java/main/com/example/avro/entities/pojo/NestedRecord.java"));
        String table = Files.readString(Path.of(
                "build/generated/sources/liquibase/1.0-create-table-COM.EXAMPLE.AVRO.ENTITIES.LIQUIBASE_NESTEDRECORD.xml"));

        // Then the polymorphic owner pair is emitted, and the SQL types match the Java types
        assertTrue(pojo.contains("public String ownerEntity;"), "Expected String ownerEntity in the pojo");
        assertTrue(pojo.contains("public long ownerId;"), "Expected long ownerId in the pojo");
        assertTrue(table.contains("<column name=\"OWNER_ENTITY\" type=\"varchar2(255)\" />"),
                "OWNER_ENTITY backs a String and must not be numeric: " + table);
        assertTrue(table.contains("<column name=\"OWNER_ID\" type=\"bigint\" />"),
                "OWNER_ID backs a long and must not be a varchar: " + table);
    }

    private void compileAndLoadGeneratedJavaFiles() throws IOException {
        Path generatedSourcesDir = Path.of("build/generated/sources/java");
        System.out.println("Looking for generated Java files in: " + generatedSourcesDir.toAbsolutePath());

        if (!Files.exists(generatedSourcesDir) || !Files.isDirectory(generatedSourcesDir)) {
            System.out.println("Generated sources directory does not exist or is not a directory");
            return; // No Java files to compile
        }

        // Find all Java files
        List<Path> javaFiles = Files.walk(generatedSourcesDir)
                .filter(path -> path.toString().endsWith(".java"))
                .toList();

        System.out.println("Found " + javaFiles.size() + " Java files to compile");
        if (javaFiles.isEmpty()) {
            return;
        }

        // Log the first few files for verification
        javaFiles.stream().limit(5).forEach(file -> System.out.println("File to compile: " + file));

        // Create output directory for compiled classes
        Path classesDir = Path.of("build/classes/java/generated");
        System.out.println("Output directory for compiled classes: " + classesDir.toAbsolutePath());
        Files.createDirectories(classesDir);

        // Get the current classpath which should include all project dependencies
        String classpath = System.getProperty("java.class.path");
        System.out.println("Initial classpath length: " + classpath.length() + " characters");

        // Add the build directories to the classpath
        Path buildDir = Path.of("build/classes/java/main");
        if (Files.exists(buildDir)) {
            classpath += File.pathSeparator + buildDir.toAbsolutePath();
            System.out.println("Added build directory to classpath: " + buildDir.toAbsolutePath());
        } else {
            System.out.println("Build directory does not exist: " + buildDir.toAbsolutePath());
        }

        // Log classpath entries for debugging
        System.out.println("Classpath entries:");
        String[] classpathEntries = classpath.split(File.pathSeparator);
        for (int i = 0; i < Math.min(classpathEntries.length, 10); i++) {
            System.out.println("  " + classpathEntries[i]);
        }
        if (classpathEntries.length > 10) {
            System.out.println("  ... and " + (classpathEntries.length - 10) + " more entries");
        }

        // Compile using Java Compiler API
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("ERROR: No Java compiler available! Make sure you're running with JDK, not JRE.");
            throw new RuntimeException("No Java compiler available");
        }
        System.out.println("Java compiler found: " + compiler.getClass().getName());

        // Create diagnostic collector to capture compilation errors
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> diagnostics =
                new javax.tools.DiagnosticCollector<>();

        try (java.io.StringWriter output = new java.io.StringWriter()) {
            System.out.println("Starting compilation process...");

            javax.tools.StandardJavaFileManager fileManager =
                    compiler.getStandardFileManager(diagnostics, null, null);

            Iterable<? extends javax.tools.JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(javaFiles);

            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(classesDir.toString());
            options.add("-classpath");
            options.add(classpath);
            options.add("-proc:none");

            javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(
                    output,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits
            );

            System.out.println("Calling compiler with options: " + options);
            boolean success = task.call();
            System.out.println("Compilation " + (success ? "succeeded" : "failed"));

            if (!success) {
                System.err.println("Compilation errors:");
                for (javax.tools.Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                    System.err.format("Error on line %d in %s: %s%n",
                            diagnostic.getLineNumber(),
                            diagnostic.getSource(),
                            diagnostic.getMessage(null));
                }
                System.err.println("Compiler output: " + output);

                // Print classpath for debugging
                System.err.println("Classpath used: " + classpath);

                throw new RuntimeException("Compilation of generated Java files failed");
            } else {
                System.out.println("Compiler output: " + output);
            }

            // Load compiled classes
            System.out.println("Creating URLClassLoader for compiled classes");
            URL[] urls = new URL[]{classesDir.toUri().toURL()};
            compiledClassLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
            System.out.println("URLClassLoader created successfully");

            // List some compiled classes to verify they exist
            listCompiledClasses(classesDir, "");

        } catch (Exception e) {
            System.err.println("Exception during compilation: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to compile generated Java files", e);
        }

        System.out.println("Compilation and class loading completed successfully");
    }

    private void listCompiledClasses(Path dir, String packagePrefix) throws IOException {
        if (!Files.exists(dir)) {
            System.out.println("Directory does not exist: " + dir);
            return;
        }

        System.out.println("Listing compiled classes in: " + dir);
        Files.list(dir).forEach(path -> {
            try {
                if (Files.isDirectory(path)) {
                    String newPrefix = packagePrefix.isEmpty() ?
                            path.getFileName().toString() :
                            packagePrefix + "." + path.getFileName().toString();
                    listCompiledClasses(path, newPrefix);
                } else if (path.toString().endsWith(".class")) {
                    String className = packagePrefix + "." + path.getFileName().toString().replace(".class", "");
                    System.out.println("Found compiled class: " + className);

                    // Try to load the class to verify it's accessible
                    try {
                        Class<?> loadedClass = compiledClassLoader.loadClass(className);
                        System.out.println("Successfully loaded class: " + loadedClass.getName());
                    } catch (ClassNotFoundException e) {
                        System.err.println("Failed to load class: " + className + " - " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Error listing classes: " + e.getMessage());
            }
        });
    }
}