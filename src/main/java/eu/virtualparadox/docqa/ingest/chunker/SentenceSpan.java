package eu.virtualparadox.docqa.ingest.chunker;

/**
 * Immutable half-open span {@code [start, end)} pointing into the source text.
 * Used to reference sentences (or fragments of long sentences).
 */
final class SentenceSpan {
    /**
     * Inclusive start offset into the source text.
     */
    final int start;
    /**
     * Exclusive end offset into the source text.
     */
    final int end;

    /**
     * Creates a sentence span.
     *
     * @param start inclusive start index (0 ≤ start ≤ end)
     * @param end   exclusive end index (start ≤ end ≤ text length)
     */
    SentenceSpan(final int start, final int end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Length of the span in characters.
     *
     * @return {@code end - start}
     */
    int length() {
        return end - start;
    }
}
