package eu.nurkert.porticlegun.portals;

import org.bukkit.ChatColor;

/**
 * The drawn settings menu for Java players with the resource pack: a glyph in the dropper title paints a plain
 * background with a slider bar behind each colour column. Keep in sync with the "settings menu" section of
 * scripts/generate_resourcepack.py.
 *
 * <p>The dropper centres its title by the title's width, so the background would move with the text. The title
 * therefore goes from the centre to the window's left edge, draws the background, moves to where the text starts
 * when centred on its own, and after the text back to the centre: 0 pixels wide in total, so it always starts in the
 * middle. That needs the text's width, which is known for the characters of the vanilla font listed here; a title
 * with other characters stays vanilla.
 */
final class SettingsGui {

    private static final char BACKGROUND = '\ue9d0';
    // shifts by -1, -2, -4 ... -128 pixels, combined like bits
    private static final char SHIFT_ONE = '\ue9d1';
    private static final int SHIFT_BITS = 8;
    private static final char SHIFT_LAST = '\ue9d8';

    private static final int WINDOW_WIDTH = 176;
    private static final int BACKGROUND_ADVANCE = WINDOW_WIDTH + 1;

    // advances of the vanilla font (ascii.png, accented.png, nonlatin_european.png and the space), by character
    private static final String[] WIDTHS = {
        "", "", "!',.:;i|", "`l", " \"()*I[]t{}", "<>fk",
        "#$%&+-/0123456789=?ABCDEFGHJKLMNOPQRSTUVWXYZ\\^_abcdeghjmnopqrsuvwxyzäöüÄÖÜß", "@~"
    };

    private SettingsGui() {}

    /** @return the title with the drawn background, or the plain title if its width is unknown */
    static String title(String text) {
        int width = width(ChatColor.stripColor(text));
        if (width < 0 || width > WINDOW_WIDTH) {
            return text;
        }
        int start = (WINDOW_WIDTH - width) / 2;
        int centre = WINDOW_WIDTH / 2;
        return "§f" + shift(centre) + BACKGROUND + shift(BACKGROUND_ADVANCE - start) + "§r" + text
                + shift(start + width - centre);
    }

    /** @return the title text without the drawn background, as configured */
    static String plain(String title) {
        StringBuilder text = new StringBuilder(title.length());
        for (char c : title.toCharArray()) {
            if (c != BACKGROUND && (c < SHIFT_ONE || c > SHIFT_LAST)) {
                text.append(c);
            }
        }
        return text.indexOf("§f§r") == 0 ? text.substring(4) : text.toString();
    }

    /** @return the shift characters moving the given number of pixels to the left */
    private static String shift(int pixels) {
        StringBuilder shift = new StringBuilder();
        for (int bit = SHIFT_BITS - 1; bit >= 0; bit--) {
            while (pixels >= 1 << bit) {
                shift.append((char) (SHIFT_ONE + bit));
                pixels -= 1 << bit;
            }
        }
        return shift.toString();
    }

    private static int width(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            int advance = -1;
            for (int i = 0; i < WIDTHS.length && advance < 0; i++) {
                if (WIDTHS[i].indexOf(c) >= 0) {
                    advance = i;
                }
            }
            if (advance < 0) {
                return -1;
            }
            width += advance;
        }
        return width;
    }
}
