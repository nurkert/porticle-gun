package eu.nurkert.porticlegun.rift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DialogTextTest {

    @Test
    void dropsPictographsButKeepsText() {
        assertEquals("Homes (2/5)", PaperDialogs.withoutSymbols("⌂ Homes (2/5)"));
        assertEquals("Zurück", PaperDialogs.withoutSymbols("◂ Zurück"));
        assertEquals("§a§fRiss-Atlas", PaperDialogs.withoutSymbols("§a⌖ §fRiss-Atlas"));
        assertEquals("§cLetzter Tod", PaperDialogs.withoutSymbols("§c☠ Letzter Tod"));
        assertEquals("§7Tank: §a§a§8§f3§7/§f8", PaperDialogs.withoutSymbols("§7Tank: §a■§a■§8■ §f3§7/§f8"));
        assertEquals("+4 Risse · 3/8", PaperDialogs.withoutSymbols("+4 Risse · 3/8"));
    }
}
