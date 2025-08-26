package eu.virtualparadox.docqa.ingest.parser;

import eu.virtualparadox.docqa.ingest.cleaner.TextCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextCleaner}.
 *
 * These tests cover newline handling, control chars, non-breaking spaces,
 * zero-width spaces, soft hyphens, format chars, and whitespace normalization.
 */
class TextCleanerTest {

    private TextCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new TextCleaner();
    }

    @Test
    void testSimpleTextIsUnchanged() {
        String input = "Dragons are dangerous creatures.";
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("Dragons are dangerous creatures.");
    }

    @Test
    void testLineBreaksAreReplacedWithSpaces() {
        String input = "volcanic\neruptions";
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("volcanic eruptions");
    }

    @Test
    void testMultipleLineBreaksBecomeSingleSpace() {
        String input = "line1\r\n\r\nline2";
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("line1 line2");
    }

    @Test
    void testControlCharactersAreRemoved() {
        String input = "valid\u0007text"; // includes BEL control char
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("validtext");
    }

    @Test
    void testNonBreakingSpaceIsNormalized() {
        String input = "word1\u00A0word2"; // NBSP between words
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("word1 word2");
    }

    @Test
    void testZeroWidthVariantsAreHandled() {
        String input1 = "word1\u200Bword2";
        String input2 = "word1\u200Cword2";
        String input3 = "word1\u200Dword2";
        String input4 = "word1\uFEFFword2";
        String input5 = "word1\u00A0word2";

        assertThat(cleaner.cleanText(input1)).isEqualTo("word1 word2");
        assertThat(cleaner.cleanText(input2)).isEqualTo("word1 word2");
        assertThat(cleaner.cleanText(input3)).isEqualTo("word1 word2");
        assertThat(cleaner.cleanText(input4)).isEqualTo("word1 word2");
        assertThat(cleaner.cleanText(input5)).isEqualTo("word1 word2");
    }

    @Test
    void testSoftHyphenIsRemoved() {
        String input = "Tat\u00ADyana"; // Tatyana with soft hyphen
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("Tatyana");
    }

    @Test
    void testOtherFormatCharactersAreRemoved() {
        // U+202C POP DIRECTIONAL FORMATTING is a common PDF artifact
        String input = "word1\u202Cword2";
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("word1 word2");
    }

    @Test
    void testMultipleSpacesAreCollapsed() {
        String input = "word1    word2   word3";
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("word1 word2 word3");
    }

    @Test
    void testLeadingAndTrailingSpacesAreTrimmed() {
        String input = "   surrounded by spaces   ";
        String output = cleaner.cleanText(input);
        assertThat(output).isEqualTo("surrounded by spaces");
    }

    @Test
    void testCombinedScenario() {
        String input = "line1\u00A0line2\nline3\u200Bline4   line5\u0008line6\u00ADend";
        String output = cleaner.cleanText(input);
        // Expected: NBSP -> space, newline -> space, zero-width -> space,
        // multiple spaces collapsed, control char removed, soft hyphen removed
        assertThat(output).isEqualTo("line1 line2 line3 line4 line5line6end");
    }
}
