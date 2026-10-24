package dev.sharp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();

    private Path file() {
        return dir.resolve("config").resolve("sharp.json");
    }

    private SharpConfig load() {
        return ConfigStore.load(file(), warnings::add);
    }

    private void write(String text) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), text, StandardCharsets.UTF_8);
    }

    @Test
    void missingFileIsCreatedWithDefaults() throws IOException {
        SharpConfig c = load();
        assertEquals(ConfigStore.toJson(new SharpConfig()), ConfigStore.toJson(c));
        assertTrue(Files.exists(file()), "defaults should be written so the user can edit them");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        SharpConfig c = new SharpConfig();
        c.range = 22.5;
        c.cameraTrackingEnabled = true;
        c.practiceMarker = "target";
        c.hitboxMainColor = "#112233";
        ConfigStore.save(file(), c);

        SharpConfig back = load();
        assertEquals(ConfigStore.toJson(c.sanitize()), ConfigStore.toJson(back));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void saveLeavesNoTemporaryFileBehind() throws IOException {
        ConfigStore.save(file(), new SharpConfig());
        try (var files = Files.list(file().getParent())) {
            assertEquals(List.of("sharp.json"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void partialFileKeepsItsValuesFillsTheRestAndIsRewritten() throws IOException {
        write("{ \"range\": 30, \"cameraTrackingEnabled\": true }");
        SharpConfig c = load();
        assertEquals(30.0, c.range);
        assertTrue(c.cameraTrackingEnabled);
        assertEquals(140.0, c.fovDegrees, "missing key keeps its default");

        String rewritten = Files.readString(file());
        assertTrue(rewritten.contains("fovDegrees"), "missing options should now appear in the file");
        assertTrue(rewritten.contains("\"range\": 30.0"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void outOfRangeValuesAreClampedAndWrittenBack() throws IOException {
        write("{ \"range\": 5000, \"cameraSmoothing\": -2, \"practiceMarker\": \"  \" }");
        SharpConfig c = load();
        assertEquals(128.0, c.range);
        assertEquals(0.0, c.cameraSmoothing);
        assertEquals(SharpConfig.DEFAULT_MARKER, c.practiceMarker);

        SharpConfig again = load();
        assertEquals(128.0, again.range);
        assertTrue(Files.readString(file()).contains("\"range\": 128.0"));
    }

    @Test
    void unknownKeysAreIgnored() throws IOException {
        write("{ \"range\": 20, \"somethingFromTheFuture\": [1,2,3] }");
        assertEquals(20.0, load().range);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidJsonIsBackedUpNotDeleted() throws IOException {
        write("{ this is not json");
        SharpConfig c = load();
        assertEquals(ConfigStore.toJson(new SharpConfig()), ConfigStore.toJson(c));
        assertEquals(1, warnings.size());

        Path backup = file().resolveSibling("sharp.json.broken");
        assertTrue(Files.exists(backup));
        assertEquals("{ this is not json", Files.readString(backup));
        assertTrue(Files.exists(file()), "a fresh default file replaces the broken one");
    }

    @Test
    void wrongValueTypeIsTreatedAsBroken() throws IOException {
        write("{ \"range\": \"far away\" }");
        SharpConfig c = load();
        assertEquals(64.0, c.range);
        assertEquals(1, warnings.size());
        assertTrue(Files.exists(file().resolveSibling("sharp.json.broken")));
    }

    @Test
    void nonObjectTopLevelIsTreatedAsBroken() throws IOException {
        for (String text : new String[] {"[1, 2, 3]", "42", "\"hello\"", "null", ""}) {
            warnings.clear();
            write(text);
            SharpConfig c = load();
            assertEquals(64.0, c.range, "for input: " + text);
            assertFalse(warnings.isEmpty(), "for input: " + text);
        }
    }

    @Test
    void anUnwritableLocationWarnsButStillReturnsDefaults() throws IOException {
        // A regular file where the config directory should be makes every write fail.
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "x");
        SharpConfig c = ConfigStore.load(blocker.resolve("sharp.json"), warnings::add);
        assertEquals(64.0, c.range);
        assertFalse(warnings.isEmpty());
    }

    /** What the first release wrote: every key it knew, with its old defaults, and no version. */
    private static final String VERSION_ONE_FILE = """
            {
              "enabled": true,
              "practiceMarker": "dummy",
              "range": 16.0,
              "fovDegrees": 60.0,
              "aimHeight": 0.5,
              "cameraTrackingEnabled": true,
              "cameraSensitivity": 1.0,
              "cameraSmoothing": 0.5,
              "maxRotationSpeed": 240.0,
              "hitboxSelectedColor": "#FFD23F",
              "hitboxTrackingColor": "#3FE0FF",
              "hitboxCandidateColor": "#9AA5B1",
              "debugOverlay": true
            }
            """;

    @Test
    void aFileFromTheFirstReleaseGetsTheNewDefaultsAndKeepsOnlyItsSwitches() throws IOException {
        write(VERSION_ONE_FILE);
        SharpConfig c = load();

        assertEquals(64.0, c.range, "the old default range is not carried over");
        assertEquals(140.0, c.fovDegrees);
        assertEquals(0.3, c.aimHeight);
        assertEquals(2.0, c.cameraSensitivity);
        assertEquals("dummy", c.practiceMarker, "the marker is the player's own choice");
        assertTrue(c.cameraTrackingEnabled, "so is camera tracking");
        assertTrue(c.debugOverlay);
        assertEquals(SharpConfig.CURRENT_VERSION, c.configVersion);

        assertEquals(1, warnings.size(), "the player is told in the log");
        Path backup = file().resolveSibling("sharp.json.v1.bak");
        assertTrue(Files.exists(backup), "the old file is kept");
        assertEquals(VERSION_ONE_FILE, Files.readString(backup));

        String rewritten = Files.readString(file());
        assertTrue(rewritten.contains("\"configVersion\": 2"), rewritten);
        assertFalse(rewritten.contains("hitboxSelectedColor"), "old keys are gone from the new file");
    }

    @Test
    void migrationHappensOnlyOnce() throws IOException {
        write(VERSION_ONE_FILE);
        load();
        warnings.clear();
        SharpConfig again = load();
        assertEquals(64.0, again.range);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void aCurrentFileKeepsEveryValueTheCurrentVersionWrote() throws IOException {
        write("{ \"configVersion\": 2, \"range\": 20, \"fovDegrees\": 90 }");
        SharpConfig c = load();
        assertEquals(20.0, c.range);
        assertEquals(90.0, c.fovDegrees);
        assertTrue(warnings.isEmpty());
        assertFalse(Files.exists(file().resolveSibling("sharp.json.v2.bak")));
    }

    @Test
    void aHandWrittenPartialFileWithoutAVersionIsNotMistakenForAnOldOne() throws IOException {
        write("{ \"range\": 30 }");
        assertEquals(30.0, load().range);
        assertTrue(warnings.isEmpty());
    }
}
