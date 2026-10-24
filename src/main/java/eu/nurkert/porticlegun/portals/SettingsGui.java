package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.util.GuiGlyphs;
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

    private static final int WINDOW_WIDTH = 176;
    private static final int BACKGROUND_ADVANCE = WINDOW_WIDTH + 1;

    // advances of the vanilla font (ascii.png, accented.png, nonlatin_european.png and the space), by character
    private static final String[] WIDTHS = {
        "",
        "",
        "!',.:;i|",
        "`l",
        " \"()*I[]t{}",
        "<>fk",
        "#$%&+-/0123456789=?ABCDEFGHJKLMNOPQRSTUVWXYZ\\^_abcdeghjmnopqrsuvwxyzäöüÄÖÜß",
        "@~"
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
        return "§f" + GuiGlyphs.shiftLeft(centre) + BACKGROUND + GuiGlyphs.shiftLeft(BACKGROUND_ADVANCE - start) + "§r"
                + text + GuiGlyphs.shiftLeft(start + width - centre);
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
