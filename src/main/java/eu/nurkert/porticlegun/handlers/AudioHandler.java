package eu.nurkert.porticlegun.handlers;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class AudioHandler {

    public static void playSound(Location loc, PortalSound sound) {
        loc.getWorld().playSound(loc, sound.getSound(), sound.getVolume(), sound.getPitch());
    }

    public static void playSound(Player player, PortalSound sound) {
        player.playSound(player.getEyeLocation(), sound.getSound(), sound.getVolume(), sound.getPitch());
    }

    public enum PortalSound {

        INV_OPEN(Sound.BLOCK_CHEST_OPEN, 0.5F, 5F),
        INV_CLOSE(Sound.BLOCK_CHEST_CLOSE, 0.5F, 5F),
        PORTAL_OPEN(Sound.BLOCK_BEACON_POWER_SELECT, 0.5F, 5F),
        PORTAL_CLOSE(Sound.BLOCK_BEACON_POWER_SELECT, 0.5F, 0.8F),
        GRAB_BLOCK(Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 10F),
        LET_BLOCK(Sound.BLOCK_BEACON_DEACTIVATE, 0.5F, 10F),
        DENY(Sound.BLOCK_NOTE_BLOCK_BASS, 1F, 0.75F),
        CLICK(Sound.BLOCK_LEVER_CLICK, 1F, 1F);

        Sound sound;
        float volume, pitch;

        PortalSound(Sound sound, float value0, float value1) {
            this.sound = sound;
            this.volume = value0;
            this.pitch = value1;

        }

        public Sound getSound() {
            return sound;
        }

        public float getVolume() {
            return volume;
        }

        public float getPitch() {
            return pitch;
        }
    }
}
