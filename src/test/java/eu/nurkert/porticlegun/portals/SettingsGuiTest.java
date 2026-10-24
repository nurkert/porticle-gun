package eu.nurkert.porticlegun.portals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SettingsGuiTest {

    /** Lays the title out like the dropper: centred by its width, glyphs advancing as the resource pack defines. */
    private static int[] layout(String title) {
        int width = 0;
        for (int i = 0; i < title.length(); i++) {
            width += advance(title, i);
        }
        int x = (176 - width) / 2;
        int background = -1;
        int text = -1;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c == '\ue9d0') {
                background = x;
            } else if (text < 0 && c == 'E') {
                text = x;
            }
            x += advance(title, i);
        }
        return new int[] {width, background, text};
    }

    private static int advance(String title, int index) {
        char c = title.charAt(index);
        if (c == '§' || (index > 0 && title.charAt(index - 1) == '§')) {
            return 0;
        }
        if (c == '\ue9d0') {
            return 177;
        }
        if (c >= '\ue9d1' && c <= '\ue9d8') {
            return -(1 << (c - '\ue9d1'));
        }
        return switch (c) {
            case 'i', 'l' -> c == 'i' ? 2 : 3;
            case 't' -> 4;
            default -> 6;
        };
    }

    @Test
    void backgroundStartsAtTheWindowEdgeAndTextStaysCentred() {
        String text = "§8Einstellungen"; // 9 * 6 + i (2) + t (4) + 2 * l (3) = 66 wide
        int[] layout = layout(SettingsGui.title(text));
        assertEquals(0, layout[0]);
        assertEquals(0, layout[1]);
        assertEquals((176 - 66) / 2, layout[2]);
    }

    @Test
    void unknownCharactersKeepTheVanillaTitle() {
        assertEquals("§8Настройки", SettingsGui.title("§8Настройки"));
    }
}
