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
        assertEquals(GuiPageId.SOURCE_FORGE, GuiPageId.fromMenuId("forge:master").orElseThrow());
        assertEquals(GuiPageId.SOURCE_FORGE, GuiPageId.fromMenuId("forge:pet").orElseThrow());
        assertEquals(GuiPageId.SOURCE_LEGENDARY, GuiPageId.fromMenuId("legendary:master").orElseThrow());
        assertEquals(GuiPageId.SOURCE_LEGENDARY, GuiPageId.fromMenuId("legendary:pet").orElseThrow());
        assertEquals(GuiPageId.SOURCE_HELP, GuiPageId.fromMenuId("help:master").orElseThrow());
        assertEquals(GuiPageId.SOURCE_HELP, GuiPageId.fromMenuId("help:pet").orElseThrow());
        assertEquals(GuiPageId.PET_ARMOR_HELP, GuiPageId.fromMenuId("petarmor:master").orElseThrow());
        assertEquals(GuiPageId.PET_ARMOR_HELP, GuiPageId.fromMenuId("petarmor:pet").orElseThrow());
    }

    @Test
    void ignoresUnknownMenus() {
        assertTrue(GuiPageId.fromMenuId("quests:master:daily:0").isEmpty());
    }
}
