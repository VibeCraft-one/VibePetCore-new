package dev.li2fox.vibepetcore.gui;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

enum GuiPageId {
    SOURCE_MAIN("master", Set.of("main", "master"), false),
    SOURCE_QUESTS("quests", Set.of("quests"), true),
    SOURCE_BOX("box", Set.of("box"), true),
    SOURCE_FORGE("forge", Set.of("forge"), true),
    SOURCE_LEGENDARY("legendary", Set.of("legendary"), true),
    SOURCE_HELP("help", Set.of("help"), true),
    PET_ARMOR_HELP("petarmor", Set.of("petarmor"), true),
    GROWTH_MISSING("growth", Set.of("growth"), true),
    PET_INFO("petinfo", Set.of("petinfo"), true),
    PET_OVERVIEW("pet", Set.of("pet"), false);

    private final String menuId;
    private final Set<String> aliases;
    private final boolean prefixMatch;

    GuiPageId(String menuId, Set<String> aliases, boolean prefixMatch) {
        this.menuId = menuId;
        this.aliases = aliases;
        this.prefixMatch = prefixMatch;
    }

    String menuId() {
        return menuId;
    }

    static Optional<GuiPageId> fromMenuId(String rawMenuId) {
        if (rawMenuId == null || rawMenuId.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawMenuId.toLowerCase(Locale.ROOT);
        for (GuiPageId pageId : values()) {
            if (pageId.aliases.contains(normalized)) {
                return Optional.of(pageId);
            }
            if (pageId.prefixMatch && pageId.aliases.stream().anyMatch(alias -> normalized.startsWith(alias + ":"))) {
                return Optional.of(pageId);
            }
        }
        return Optional.empty();
    }
}
