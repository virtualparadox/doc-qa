package eu.virtualparadox.docqa.query.citation.pageinterval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Utility component for merging overlapping or adjacent page intervals.
 * <p>
 * This is typically used in citation or document processing workflows
 * where page ranges from multiple references need to be normalized
 * into a minimal, non-overlapping set of intervals.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Overlapping intervals are merged into a single interval.</li>
 *   <li>Adjacent intervals (where {@code current.toPage + 1 == next.fromPage})
 *       are merged as well.</li>
 *   <li>Intervals are validated (fromPage ≤ toPage), sorted by {@code fromPage}
 *       and then merged in one linear pass.</li>
 *   <li>Null input or an empty list yields an empty list.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * Input:
 * <pre>
 *   [1..3], [2..5], [7..7], [8..10]
 * </pre>
 * Output:
 * <pre>
 *   [1..5], [7..10]
 * </pre>
 */
@Component
public class PageIntervalMerger {

    /**
     * Merge a list of {@link PageInterval} objects into a minimal set
     * of non-overlapping, non-adjacent intervals.
     *
     * @param intervals list of intervals to merge; may be {@code null} or empty
     * @return merged, sorted list of intervals (never {@code null})
     * @throws IllegalArgumentException if any interval has {@code fromPage > toPage}
     */
    public List<PageInterval> merge(final List<PageInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return new ArrayList<>();
        }

        // Validate and copy to avoid mutating input
        final List<PageInterval> validIntervals = new ArrayList<>(intervals.size());
        for (PageInterval interval : intervals) {
            Objects.requireNonNull(interval, "interval must not be null");
            if (interval.fromPage() > interval.toPage()) {
                throw new IllegalArgumentException(
                        "Invalid interval: fromPage (" + interval.fromPage()
                                + ") cannot be greater than toPage (" + interval.toPage() + ")"
                );
            }
            validIntervals.add(interval);
        }

        // Sort by start, then by end
        validIntervals.sort(
                Comparator.comparingInt(PageInterval::fromPage)
                        .thenComparingInt(PageInterval::toPage)
        );

        final List<PageInterval> merged = new ArrayList<>();
        PageInterval current = validIntervals.get(0);

        for (int i = 1; i < validIntervals.size(); i++) {
            PageInterval next = validIntervals.get(i);

            // Overlap or adjacency: extend the current interval
            if (current.toPage() + 1 >= next.fromPage()) {
                current = new PageInterval(
                        current.fromPage(),
                        Math.max(current.toPage(), next.toPage())
                );
            } else {
                // No overlap: push current and advance
                merged.add(current);
                current = next;
            }
        }

        merged.add(current);
        return merged;
    }
}
