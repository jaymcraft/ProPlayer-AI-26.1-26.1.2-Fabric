package net.shasankp000;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictSurvivalGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path ALLOWED_SPAWN_FILE = Path.of("src/main/java/net/shasankp000/Entity/createFakePlayer.java");

    @Test
    void onlySpawnInitializationMayTeleportPlayers() throws IOException {
        List<String> violations = javaFiles().stream()
                .filter(path -> !path.equals(ALLOWED_SPAWN_FILE))
                .flatMap(path -> linesContaining(path, "teleportTo(").stream())
                .toList();

        assertTrue(violations.isEmpty(), "Strict survival forbids runtime teleport shortcuts:\n" + String.join("\n", violations));
    }

    @Test
    void miningMustNotUseInstantDestroyBlock() throws IOException {
        List<String> violations = javaFiles().stream()
                .flatMap(path -> linesContaining(path, "destroyBlock(").stream())
                .toList();

        assertTrue(violations.isEmpty(), "Strict survival mining must not call destroyBlock directly:\n" + String.join("\n", violations));
    }

    @Test
    void teleportForwardCommandMustStayRemoved() throws IOException {
        List<String> violations = javaFiles().stream()
                .flatMap(path -> linesContaining(path, "teleport_forward").stream())
                .toList();

        assertTrue(violations.isEmpty(), "Strict survival must not expose a teleport-forward command:\n" + String.join("\n", violations));
    }

    private static List<Path> javaFiles() throws IOException {
        try (var stream = Files.walk(SOURCE_ROOT)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<String> linesContaining(Path path, String needle) {
        try {
            List<String> lines = Files.readAllLines(path);
            java.util.ArrayList<String> matches = new java.util.ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(needle)) {
                    matches.add(path + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
            return matches;
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + path, e);
        }
    }
}
