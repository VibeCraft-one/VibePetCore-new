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

    @Test
    void petMovementTextsDoNotPromiseMissingGuiModeToggle() throws IOException {
        String checklist = read("TEST_CHECKLIST.md");
        assertFalse(checklist.contains("\u043a\u043d\u043e\u043f\u043a\u0430 `\u0420\u0435\u0436\u0438\u043c \u0434\u0432\u0438\u0436\u0435\u043d\u0438\u044f`"));
        assertTrue(checklist.contains("\u043f\u043e\u0437\u0438\u0446\u0438\u0438 0-7"));
        assertTrue(checklist.contains("`/pet stay` \u0438 `/pet follow`"));

        String ruMessages = read("src/main/resources/messages/ru.yml");
        String enMessages = read("src/main/resources/messages/en.yml");
        assertFalse(ruMessages.contains("\u041a\u0440\u0430\u0441\u043d\u044b\u0435 \u0438 \u0437\u0435\u043b\u0451\u043d\u044b\u0435 \u043a\u043d\u043e\u043f\u043a\u0438 \u043c\u0435\u043d\u044f\u044e\u0442 \u0440\u0435\u0436\u0438\u043c"));
        assertFalse(enMessages.contains("Red and green choose the mode"));
        assertTrue(ruMessages.contains("\u0411\u043b\u0438\u0436\u0435/\u0414\u0430\u043b\u044c\u0448\u0435 \u043c\u0435\u043d\u044f\u044e\u0442 \u0434\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044e"));
        assertTrue(enMessages.contains("Closer/Further changes distance"));
    }

    @Test
    void petOverviewSummonTextsRequireOffhandCore() throws IOException {
        String ruMessages = read("src/main/resources/messages/ru.yml");
        String enMessages = read("src/main/resources/messages/en.yml");

        for (String line : List.of(
            lineStartingWith(ruMessages, "gui.pet.summon.line.one:"),
            lineStartingWith(ruMessages, "gui.pet.summon.line.three:"),
            lineStartingWith(ruMessages, "gui.pet.overview.need-core-hint:"),
            lineStartingWith(ruMessages, "gui.pet.overview.summon.hint:")
        )) {
            assertTrue(line.contains("\u0432\u0442\u043e\u0440"), line);
            assertFalse(line.contains("\u043e\u0441\u043d\u043e\u0432\u043d"), line);
            assertFalse(line.contains("\u0432 \u043b\u044e\u0431\u043e\u0439 \u0440\u0443\u043a\u0435"), line);
        }

        for (String line : List.of(
            lineStartingWith(enMessages, "gui.pet.summon.line.one:"),
            lineStartingWith(enMessages, "gui.pet.summon.line.three:"),
            lineStartingWith(enMessages, "gui.pet.overview.need-core-hint:"),
            lineStartingWith(enMessages, "gui.pet.overview.summon.hint:")
        )) {
            String lower = line.toLowerCase(Locale.ROOT);
            assertTrue(lower.contains("offhand"), line);
            assertFalse(lower.contains("main hand"), line);
            assertFalse(lower.contains("either hand"), line);
        }
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
