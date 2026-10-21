package eu.nurkert.porticlegun.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BedrockPackInstallerTest {

    private static YamlConfiguration yaml(String content) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(content);
        return config;
    }

    @Test
    void findsSettingsInCurrentGeyserConfigs() throws InvalidConfigurationException {
        YamlConfiguration config = yaml("gameplay:\n  enable-custom-content: false\n  force-resource-packs: true\n");
        assertEquals(Boolean.FALSE, BedrockPackInstaller.findBoolean(config, "enable-custom-content"));
        assertEquals(Boolean.TRUE, BedrockPackInstaller.findBoolean(config, "force-resource-packs"));
    }

    @Test
    void findsSettingsInOldGeyserConfigs() throws InvalidConfigurationException {
        YamlConfiguration config = yaml("enable-custom-content: false\n");
        assertEquals(Boolean.FALSE, BedrockPackInstaller.findBoolean(config, "enable-custom-content"));
    }

    @Test
    void ignoresMissingSettingsAndSimilarNames() throws InvalidConfigurationException {
        YamlConfiguration config = yaml("gameplay:\n  not-enable-custom-content: false\n");
        assertNull(BedrockPackInstaller.findBoolean(config, "enable-custom-content"));
    }
}
