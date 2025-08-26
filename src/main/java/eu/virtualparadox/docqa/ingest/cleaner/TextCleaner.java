package eu.virtualparadox.docqa.ingest.cleaner;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextCleaner {

    /**
     * Cleans extracted text by removing control characters, zero-width spaces,
     * and normalizing whitespace while keeping diacritics.
     *
     * @param input raw text
     * @return cleaned text
     */
    public String cleanText(final String input) {
        return cleanTextWithPageMapping(input, new int[input.length()]).getCleanText();
    }

    /**
     * Cleans extracted text while maintaining character-to-page mapping.
     * Characters that are removed get their page mapping entries removed too.
     * Characters that are replaced keep their original page mapping.
     *
     * @param input raw text
     * @param pageMap page mapping array (can be null for simple text cleaning)
     * @return CleaningResult with cleaned text and adjusted page mapping
     */
    public CleaningResult cleanTextWithPageMapping(final String input, final int[] pageMap) {
        if (input == null || input.isEmpty()) {
            return new CleaningResult("", new int[0]);
        }

        if (pageMap == null) {
            // No page mapping provided, create empty mapping for result
            String cleanedText = performCleaning(input);
            return new CleaningResult(cleanedText, new int[0]);
        }

        if (input.length() != pageMap.length) {
            throw new IllegalArgumentException("Text length must equal page map length");
        }

        // Apply cleaning transformations step by step with page mapping
        return performCleaningWithPageMapping(input, pageMap);
    }

    private String performCleaning(String input) {
        return input
                // line breaks -> space
                .replaceAll("[\\r\\n]+", " ")
                // zero-width and similar → SPACE
                .replaceAll("[\\u200B\\u200C\\u200D\\uFEFF]", " ")
                // non-breaking space → SPACE
                .replace("\u00A0", " ")
                // soft hyphen (0xAD) → remove
                .replace("\u00AD", "")
                // other format chars → SPACE (instead of remove)
                .replaceAll("\\p{Cf}", " ")
                // control chars → remove
                .replaceAll("\\p{Cc}", "")
                // collapse multiple whitespace → single space
                .replaceAll("\\s+", " ")
                .trim();
    }

    private CleaningResult performCleaningWithPageMapping(String input, int[] pageMap) {
        // Apply cleaning transformations step by step
        String workingText = input;
        int[] workingPageMap = pageMap.clone();

        // Step 1: Convert line breaks to spaces
        CleaningStep step1 = replacePattern(workingText, workingPageMap, "[\\r\\n]+", " ");
        workingText = step1.text;
        workingPageMap = step1.pageMap;

        // Step 2: Convert zero-width characters to spaces
        CleaningStep step2 = replacePattern(workingText, workingPageMap, "[\\u200B\\u200C\\u200D\\uFEFF]", " ");
        workingText = step2.text;
        workingPageMap = step2.pageMap;

        // Step 3: Convert non-breaking space to regular space
        CleaningStep step3 = replaceChar(workingText, workingPageMap, '\u00A0', ' ');
        workingText = step3.text;
        workingPageMap = step3.pageMap;

        // Step 4: Remove soft hyphen
        CleaningStep step4 = removeChar(workingText, workingPageMap, '\u00AD');
        workingText = step4.text;
        workingPageMap = step4.pageMap;

        // Step 5: Replace format characters with spaces
        CleaningStep step5 = replacePattern(workingText, workingPageMap, "\\p{Cf}", " ");
        workingText = step5.text;
        workingPageMap = step5.pageMap;

        // Step 6: Remove control characters
        CleaningStep step6 = removePattern(workingText, workingPageMap, "\\p{Cc}");
        workingText = step6.text;
        workingPageMap = step6.pageMap;

        // Step 7: Collapse multiple whitespace to single space
        CleaningStep step7 = collapseWhitespace(workingText, workingPageMap);
        workingText = step7.text;
        workingPageMap = step7.pageMap;

        // Step 8: Trim leading/trailing whitespace
        CleaningStep step8 = trimText(workingText, workingPageMap);

        return new CleaningResult(step8.text, step8.pageMap);
    }

    // === Private helper methods ===

    private static class CleaningStep {
        final String text;
        final int[] pageMap;

        CleaningStep(String text, int[] pageMap) {
            this.text = text;
            this.pageMap = pageMap;
        }
    }

    private CleaningStep replaceChar(String text, int[] pageMap, char oldChar, char newChar) {
        return new CleaningStep(
                text.replace(oldChar, newChar),
                pageMap.clone() // Page mapping stays the same for 1:1 replacements
        );
    }

    private CleaningStep removeChar(String text, int[] pageMap, char charToRemove) {
        final StringBuilder result = new StringBuilder();
        final List<Integer> resultPageMap = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != charToRemove) {
                result.append(text.charAt(i));
                resultPageMap.add(pageMap[i]);
            }
            // Skip characters that match (remove them and their page mapping)
        }

        return new CleaningStep(
                result.toString(),
                resultPageMap.stream().mapToInt(Integer::intValue).toArray()
        );
    }

    private CleaningStep replacePattern(String text, int[] pageMap, String pattern, String replacement) {
        final StringBuilder result = new StringBuilder();
        final List<Integer> resultPageMap = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            String singleChar = String.valueOf(text.charAt(i));
            if (singleChar.matches(pattern)) {
                // Replace with the replacement string (assuming single char replacement)
                result.append(replacement);
                resultPageMap.add(pageMap[i]);
            } else {
                result.append(text.charAt(i));
                resultPageMap.add(pageMap[i]);
            }
        }

        return new CleaningStep(
                result.toString(),
                resultPageMap.stream().mapToInt(Integer::intValue).toArray()
        );
    }

    private CleaningStep removePattern(String text, int[] pageMap, String pattern) {
        final StringBuilder result = new StringBuilder();
        final List<Integer> resultPageMap = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            String singleChar = String.valueOf(text.charAt(i));
            if (!singleChar.matches(pattern)) {
                result.append(text.charAt(i));
                resultPageMap.add(pageMap[i]);
            }
            // Skip characters that match the pattern
        }

        return new CleaningStep(
                result.toString(),
                resultPageMap.stream().mapToInt(Integer::intValue).toArray()
        );
    }

    private CleaningStep collapseWhitespace(String text, int[] pageMap) {
        final StringBuilder result = new StringBuilder();
        final List<Integer> resultPageMap = new ArrayList<>();

        boolean lastWasSpace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isSpace = Character.isWhitespace(c);

            if (isSpace) {
                if (!lastWasSpace) {
                    // First space in a sequence, keep it
                    result.append(' ');
                    resultPageMap.add(pageMap[i]);
                    lastWasSpace = true;
                }
                // Skip additional spaces in sequence
            } else {
                // Non-space character, always keep
                result.append(c);
                resultPageMap.add(pageMap[i]);
                lastWasSpace = false;
            }
        }

        return new CleaningStep(
                result.toString(),
                resultPageMap.stream().mapToInt(Integer::intValue).toArray()
        );
    }

    private CleaningStep trimText(String text, int[] pageMap) {
        int start = 0;
        int end = text.length();

        // Find first non-whitespace character
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        // Find last non-whitespace character
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        if (start >= end) {
            // All whitespace
            return new CleaningStep("", new int[0]);
        }

        // Extract the trimmed text and corresponding page mappings
        final String trimmedText = text.substring(start, end);
        final int[] trimmedPageMap = new int[end - start];
        System.arraycopy(pageMap, start, trimmedPageMap, 0, end - start);

        return new CleaningStep(trimmedText, trimmedPageMap);
    }
}