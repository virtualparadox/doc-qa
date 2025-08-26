package eu.virtualparadox.docqa.query.citation.pageinterval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive tests for {@link PageIntervalMerger}.
 */
class PageIntervalMergerTest {

    private final PageIntervalMerger merger = new PageIntervalMerger();

    @Test
    @DisplayName("Null input yields empty list")
    void testNullInput() {
        assertTrue(merger.merge(null).isEmpty());
    }

    @Test
    @DisplayName("Empty input yields empty list")
    void testEmptyInput() {
        assertTrue(merger.merge(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Single interval remains unchanged")
    void testSingleInterval() {
        List<PageInterval> result = merger.merge(
                Collections.singletonList(new PageInterval(1, 5))
        );
        assertEquals(List.of(new PageInterval(1, 5)), result);
    }

    @Test
    @DisplayName("Non-overlapping intervals remain separate and sorted")
    void testNonOverlappingIntervals() {
        List<PageInterval> input = Arrays.asList(
                new PageInterval(5, 6),
                new PageInterval(1, 3)
        );
        List<PageInterval> expected = Arrays.asList(
                new PageInterval(1, 3),
                new PageInterval(5, 6)
        );
        assertEquals(expected, merger.merge(input));
    }

    @Test
    @DisplayName("Overlapping intervals are merged")
    void testOverlappingIntervals() {
        List<PageInterval> input = Arrays.asList(
                new PageInterval(1, 4),
                new PageInterval(3, 6)
        );
        List<PageInterval> expected = List.of(new PageInterval(1, 6));
        assertEquals(expected, merger.merge(input));
    }

    @Test
    @DisplayName("Adjacent intervals are merged")
    void testAdjacentIntervals() {
        List<PageInterval> input = Arrays.asList(
                new PageInterval(1, 3),
                new PageInterval(4, 6)
        );
        List<PageInterval> expected = List.of(new PageInterval(1, 6));
        assertEquals(expected, merger.merge(input));
    }

    @Test
    @DisplayName("Mixed overlapping and adjacent intervals are merged correctly")
    void testMixedIntervals() {
        List<PageInterval> input = Arrays.asList(
                new PageInterval(1, 3),
                new PageInterval(2, 4),
                new PageInterval(5, 5),
                new PageInterval(6, 8),
                new PageInterval(10, 12)
        );
        List<PageInterval> expected = Arrays.asList(
                new PageInterval(1, 8),
                new PageInterval(10, 12)
        );
        assertEquals(expected, merger.merge(input));
    }

    @Test
    @DisplayName("Invalid interval (fromPage > toPage) throws exception")
    void testInvalidInterval() {
        List<PageInterval> input = List.of(new PageInterval(5, 3));
        assertThrows(IllegalArgumentException.class, () -> merger.merge(input));
    }

    @Test
    @DisplayName("Null interval inside list throws exception")
    void testNullIntervalInList() {
        List<PageInterval> input = Arrays.asList(
                new PageInterval(1, 2),
                null
        );
        assertThrows(NullPointerException.class, () -> merger.merge(input));
    }

    @Test
    @DisplayName("Complex case: multiple merges with sorting")
    void testComplexCase() {
        List<PageInterval> input = Arrays.asList(
                new PageInterval(10, 15),
                new PageInterval(1, 2),
                new PageInterval(3, 5),
                new PageInterval(5, 8),
                new PageInterval(16, 18),
                new PageInterval(18, 20)
        );
        List<PageInterval> expected = Arrays.asList(
                new PageInterval(1, 8),
                new PageInterval(10, 20)
        );
        assertEquals(expected, merger.merge(input));
    }
}
