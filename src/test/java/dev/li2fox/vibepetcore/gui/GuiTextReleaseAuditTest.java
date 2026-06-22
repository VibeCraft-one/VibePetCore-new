package dev.li2fox.vibepetcore.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class GuiTextReleaseAuditTest {
    @Test
    void petOverviewRequirementSummaryDoesNotExposeBond() throws IOException {
        for (String resource : List.of("src/main/resources/messages/ru.yml", "src/main/resources/messages/en.yml")) {
            String text = read(resource);
            assertNoBondVocabulary(lineStartingWith(text, "gui.pet.requirements.summary:"), resource);
            assertNoBondVocabulary(lineStartingWith(text, "gui.pet.requirements.summary-quests:"), resource);
        }
    }

    @Test
    void testerChecklistUsesSourceAsPrimaryAdminCommand() throws IOException {
        String checklist = read("TEST_CHECKLIST.md");
        assertTrue(checklist.contains("/vpc source set"));
        assertFalse(checklist.contains("/vpc master set"));
        assertFalse(checklist.contains("\u041f\u0435\u0442-\u041c\u0430\u0441\u0442\u0435\u0440"));
    }

    private static void assertNoBondVocabulary(String line, String source) {
        String lower = line.toLowerCase(Locale.ROOT);
        assertFalse(lower.contains("bond"), source + " should keep bond details inside the evolution page");
        assertFalse(lower.contains("\u0441\u0432\u044f\u0437"), source + " should keep bond details inside the evolution page");
    }

    private static String lineStartingWith(String text, String prefix) {
        return text.lines()
            .filter(line -> line.startsWith(prefix))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing line: " + prefix));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
