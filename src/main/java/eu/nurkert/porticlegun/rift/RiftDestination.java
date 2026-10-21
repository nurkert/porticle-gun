package eu.nurkert.porticlegun.rift;

/**
 * Where the interdimensional gun opens its rifts: a random safe spot in a world, a world's spawn, one of the player's
 * homes, back to where the player last stepped through a rift, or where the player last died. Stored on the gun item
 * as {@code world:<name>}, {@code spawn:<name>}, {@code home:<n>}, {@code back} or {@code death}.
 */
public record RiftDestination(Type type, String world, int home) {

    public enum Type {
        WORLD,
        SPAWN,
        HOME,
        BACK,
        DEATH
    }

    public static RiftDestination world(String name) {
        return new RiftDestination(Type.WORLD, name, -1);
    }

    public static RiftDestination spawn(String name) {
        return new RiftDestination(Type.SPAWN, name, -1);
    }

    public static RiftDestination death() {
        return new RiftDestination(Type.DEATH, null, -1);
    }

    public static RiftDestination home(int index) {
        return new RiftDestination(Type.HOME, null, index);
    }

    public static RiftDestination back() {
        return new RiftDestination(Type.BACK, null, -1);
    }

    /**
     * @return the destination, or {@code null} if the text is not a valid destination
     */
    public static RiftDestination parse(String text) {
        if (text == null) {
            return null;
        }
        if (text.equals("back")) {
            return back();
        }
        if (text.equals("death")) {
            return death();
        }
        if (text.startsWith("spawn:") && text.length() > "spawn:".length()) {
            return spawn(text.substring("spawn:".length()));
        }
        if (text.startsWith("world:") && text.length() > "world:".length()) {
            return world(text.substring("world:".length()));
        }
        if (text.startsWith("home:")) {
            try {
                int index = Integer.parseInt(text.substring("home:".length()));
                return index >= 0 ? home(index) : null;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    public String serialize() {
        return switch (type) {
            case WORLD -> "world:" + world;
            case SPAWN -> "spawn:" + world;
            case HOME -> "home:" + home;
            case BACK -> "back";
            case DEATH -> "death";
        };
    }
}
