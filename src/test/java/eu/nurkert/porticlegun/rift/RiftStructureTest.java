package eu.nurkert.porticlegun.rift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RiftStructureTest {

    @Test
    void idsAreUniqueAndResolve() {
        Set<String> ids = new HashSet<>();
        for (RiftStructure structure : RiftStructure.values()) {
            assertFalse(ids.contains(structure.id()), structure.id());
            ids.add(structure.id());
            assertEquals(structure, RiftStructure.byId(structure.id()));
            assertFalse(structure.variants().isEmpty(), structure.id());
        }
    }

    @Test
    void everyStructureHasANameInEveryLanguage() {
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getClass().getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
        for (String language : new String[] {"en", "de"}) {
            for (RiftStructure structure : RiftStructure.values()) {
                assertNotNull(
                        messages.getString(language + ".rift.structure." + structure.id()),
                        language + " " + structure.id());
            }
        }
    }
}
