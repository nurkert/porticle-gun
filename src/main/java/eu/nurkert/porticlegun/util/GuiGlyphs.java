package eu.nurkert.porticlegun.util;

/**
 * Drawn menus for Java players with the resource pack: a glyph in the title paints the window's top half, moved into
 * place with shift glyphs (the "settings menu" and "command menus" sections of scripts/generate_resourcepack.py).
 */
public final class GuiGlyphs {

    // shifts by -1, -2, -4 ... -128 pixels, combined like bits
    private static final char SHIFT_ONE = '\ue9d1';
    private static final int SHIFT_BITS = 8;

    // a chest title starts 8 pixels right of the window; one 176 pixel wide glyph advances by 177
    private static final int CHEST_TITLE_X = 8;
    private static final int BACKGROUND_ADVANCE = 177;

    private GuiGlyphs() {}

    /** @return the shift characters moving the given number of pixels to the left */
    public static String shiftLeft(int pixels) {
        StringBuilder shift = new StringBuilder();
        for (int bit = SHIFT_BITS - 1; bit >= 0; bit--) {
            while (pixels >= 1 << bit) {
                shift.append((char) (SHIFT_ONE + bit));
                pixels -= 1 << bit;
            }
        }
        return shift.toString();
    }

    /**
     * @return a chest title drawing the background glyph over the window's top half (white, so it keeps its colours),
     *     then the title text where it would be anyway
     */
    public static String chestTitle(char background, String text) {
        return "§f" + shiftLeft(CHEST_TITLE_X) + background + shiftLeft(BACKGROUND_ADVANCE - CHEST_TITLE_X) + "§r"
                + text;
    }
}
