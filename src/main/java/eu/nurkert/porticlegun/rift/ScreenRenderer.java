package eu.nurkert.porticlegun.rift;

import org.bukkit.entity.Player;

/** Shows {@link RiftScreen}s natively on one kind of client. */
interface ScreenRenderer {

    /** Shows the screen; button actions arrive on the main thread. */
    void show(Player player, RiftScreen screen);

    /** Closes whatever screen of this kind the player has open. */
    void close(Player player);
}
