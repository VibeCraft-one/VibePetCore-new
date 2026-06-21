package dev.li2fox.vibepetcore.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GuiPageIdTest {
    @Test
    void resolvesStaticPageIds() {
        assertEquals(GuiPageId.SOURCE_MAIN, GuiPageId.fromMenuId("master").orElseThrow());
        assertEquals(GuiPageId.SOURCE_MAIN, GuiPageId.fromMenuId("main").orElseThrow());
        assertEquals(GuiPageId.PET_OVERVIEW, GuiPageId.fromMenuId("pet").orElseThrow());
    }

    @Test
    void resolvesPrefixPageIds() {
        assertEquals(GuiPageId.SOURCE_BOX, GuiPageId.fromMenuId("box:master").orElseThrow());
        assertEquals(GuiPageId.SOURCE_BOX, GuiPageId.fromMenuId("box:pet").orElseThrow());
    }

    @Test
    void ignoresUnknownMenus() {
        assertTrue(GuiPageId.fromMenuId("quests:master:daily:0").isEmpty());
    }
}
