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

        INV_OPEN(Sound.BLOCK_IRON_DOOR_OPEN, 0.5F, 7.5F),
        INV_CLOSE(Sound.BLOCK_IRON_DOOR_CLOSE, 0.5F, 7.5F),
        PORTAL_OPEN(Sound.BLOCK_BEACON_POWER_SELECT, 0.5F, 10F),
        PORTAL_CLOSE(Sound.BLOCK_BEACON_POWER_SELECT, 0.5F, 0.8F),
        GRAB_BLOCK(Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 0.75F),
        LET_BLOCK(Sound.BLOCK_BEACON_DEACTIVATE, 0.5F, 0.75F),
        DENY(Sound.BLOCK_NOTE_BLOCK_BASS, 1F, 0.75F),
        CLICK(Sound.BLOCK_LEVER_CLICK, 1F, 2F);

        Sound sound;
        float volume, pitch;

        PortalSound(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;

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
