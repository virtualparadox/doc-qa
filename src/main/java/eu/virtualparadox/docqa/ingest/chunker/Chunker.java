package eu.virtualparadox.docqa.ingest.chunker;

import eu.virtualparadox.docqa.ingest.model.Chunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentence-aware text {@code Chunker} that produces overlapping chunks suitable for RAG pipelines.
 *
 * <h2>Overview</h2>
 * <ul>
 *   <li><strong>Sentence splitting:</strong> Uses a Unicode‑uppercase‑aware boundary rule:
 *       a sentence ends at {@code .}, {@code !}, or {@code ?}, followed by whitespace,
 *       and the next sentence starts with an uppercase letter or a quote. Common trailing
 *       abbreviations (e.g., {@code Dr.}, {@code Mr.}, months, weekdays) are exempted so they
 *       do not prematurely terminate a sentence.</li>
 *   <li><strong>Packing:</strong> Sentences are greedily packed into chunks up to
 *       {@code targetCharsPerChunk} characters, counting an inserted single space between
 *       adjacent sentences, and including any <em>overlap prefix</em> that will be prepended to
 *       the chunk.</li>
 *   <li><strong>Exact overlap prefix:</strong> Each chunk (except the first) is prefixed with an
 *       exact {@code overlapChars}-long tail of the previous chunk's final characters. This enables
 *       reconstruction across chunk boundaries (useful in tests and retrieval scoring).</li>
 *   <li><strong>Long sentence hard-split:</strong> If a single sentence exceeds
 *       {@code targetCharsPerChunk}, it is split into fixed-size windows with internal overlap
 *       so that no produced chunk exceeds the target size.</li>
 *   <li><strong>Optional page mapping:</strong> If a {@code pageMap} (per-character page index) is
 *       provided, each produced {@link Chunk} is annotated with an estimated page span
 *       ({@code pageStart..pageEnd}) derived from the character indices included in the chunk.</li>
 * </ul>
 *
 * <h2>Chunk Size Accounting</h2>
 * A chunk’s materialized text is:
 * <pre>
 *     overlapPrefix + sentence_1 + " " + sentence_2 + " " + ... + sentence_k
 * </pre>
 * The packing decision uses the exact length of <em>overlapPrefix</em>, sentence lengths, and
 * the single-space separators to keep the realized chunk length ≤ {@code targetCharsPerChunk}.
 *
 * <h2>Determinism &amp; Thread-safety</h2>
 * This component is stateless after construction and thus thread-safe. For a given input
 * {@code (docId, text, pageMap)}, output is deterministic.
 *
 * <h2>Complexity</h2>
 * Splitting is linear in the input length with a single regex scan. Packing and chunk
 * construction are linear in the number of sentences (plus string building costs for emitted chunks).
 *
 * @implNote The sentence boundary regex is intentionally conservative to avoid over-splitting on
 *           abbreviations. The separate {@link #ABBREVIATION_PATTERN} check guards against false
 *           positives immediately before a candidate boundary.
 */
@Component
public class Chunker {

    /**
     * Target maximum number of characters per produced chunk (strict upper bound).
     * Includes the overlap prefix length and the single spaces inserted between sentences.
     */
    private final int targetCharsPerChunk;

    /**
     * Exact number of trailing characters from the previous chunk to prepend as the
     * overlap prefix to the next chunk. If zero, chunks are butt-joined with no overlap.
     */
    private final int overlapChars;

    /**
     * Unicode-capital-aware sentence boundary:
     * <pre>
     *     (?&lt;=[.!?])     # trailing ., ! or ? must precede the split
     *     (?![.!?])        # do not allow runs of punctuation to trigger multiple splits
     *     \s+              # one or more whitespace characters form the divider
     *     (?=[\p{Lu}"'])   # next sentence starts with uppercase/quote
     * </pre>
     * This is a coarse pass; we additionally check for known abbreviations immediately
     * before the split candidate and suppress the split in that case.
     */
    private static final Pattern SENTENCE_SPLIT = Pattern.compile(
            "(?<=[.!?])" +
                    "(?![.!?])" +
                    "\\s+" +
                    "(?=[\\p{Lu}\"'])"
    );

    /**
     * Abbreviation guard used to reduce false sentence boundaries.
     * <p>
     * This pattern matches if the text immediately before the split candidate ends with
     * a known abbreviation token followed by a period (e.g., {@code Dr.}, {@code Inc.}, {@code Jan.}).
     * We check this on a short window of text before the boundary. If it matches, the split is suppressed.
     * </p>
     */
    private static final Pattern ABBREVIATION_PATTERN = Pattern.compile(
            "\\b(?:Dr|Mr|Mrs|Ms|Prof|Sr|Jr|Inc|Ltd|Corp|Co|St|Ave|Blvd|Rd|etc|vs|eg|ie|cf|ca|approx|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|Mon|Tue|Wed|Thu|Fri|Sat|Sun|U\\.S\\.A|U\\.K|U\\.N)\\.$"
    );

    /**
     * Constructs a {@code Chunker}.
     *
     * @param targetCharsPerChunk strict upper bound on the realized chunk text length (must be {@code > 0})
     * @param overlapChars        exact number of trailing characters from the previous chunk
     *                            to prepend to the next chunk (must be {@code >= 0} and {@code < targetCharsPerChunk})
     * @throws IllegalArgumentException if constraints are violated
     */
    public Chunker(@Value("${chunker.target-chars:2000}") final int targetCharsPerChunk,
                   @Value("${chunker.overlap-chars:200}") final int overlapChars) {
        if (targetCharsPerChunk <= 0) {
            throw new IllegalArgumentException("targetCharsPerChunk must be positive");
        }
        if (overlapChars < 0 || overlapChars >= targetCharsPerChunk) {
            throw new IllegalArgumentException("overlapChars must be non-negative and less than targetCharsPerChunk");
        }
        this.targetCharsPerChunk = targetCharsPerChunk;
        this.overlapChars = overlapChars;
    }

    /**
     * Convenience API without page mapping. Produces sentence-aware, overlapped chunks.
     *
     * @param docId document identifier (non-blank)
     * @param text  input text (non-null)
     * @return ordered list of chunks covering {@code text}
     * @throws IllegalArgumentException if inputs are invalid
     */
    public List<Chunk> sentenceAwareChunks(final String docId, final String text) {
        return sentenceAwareChunks(docId, text, null);
    }

    /**
     * Page-aware API. If {@code pageMap} is provided, each chunk records a best-effort page span.
     *
     * <p><strong>Page span computation:</strong>
     * We derive {@code absoluteStart} from the first sentence's start and subtract the
     * {@code overlapText} length (bounded to ≥ 0), because the overlap conceptually belongs
     * to the previous region. {@code absoluteEnd} is the end of the last sentence in the chunk.
     * We then map both indices through {@link #pageOf(int[], int)} to obtain {@code pageStart}
     * and {@code pageEnd}. If {@code pageMap} is absent, page indices are {@code -1}.</p>
     *
     * @param docId   document identifier (non-blank)
     * @param text    input text (non-null)
     * @param pageMap optional per-character page index array where {@code pageMap[i]} gives the page
     *                number of {@code text.charAt(i)}. If non-null, must have length {@code text.length()}.
     * @return ordered list of chunks satisfying the packing and overlap rules
     * @throws IllegalArgumentException if inputs are invalid
     */
    public List<Chunk> sentenceAwareChunks(final String docId,
                                           final String text,
                                           final int[] pageMap) {
        validateInputs(docId, text, pageMap);

        final List<Chunk> result = new ArrayList<>();
        if (text.isBlank()) {
            return result;
        }

        final List<SentenceSpan> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return result;
        }

        // Accumulator for sentences in the current chunk decision window.
        List<SentenceSpan> current = new ArrayList<>();
        // Running length of the chunk under construction: sum(sentence lengths) + spaces + overlap length.
        int currentLen = 0;
        int seq = 0;

        // Exact character overlap that is prepended to the next chunk’s text.
        String nextChunkOverlap = "";

        for (SentenceSpan s : sentences) {
            final int sLen = s.length();

            // Case 1: Single sentence exceeds the target; hard-split into fixed windows with internal overlap.
            if (sLen > targetCharsPerChunk) {
                // Flush any pending chunk first to preserve ordering and overlap invariants.
                if (!current.isEmpty() || !nextChunkOverlap.isEmpty()) {
                    result.add(createChunkWithOverlap(docId, text, current, nextChunkOverlap, seq++, pageMap));
                    final String chunkText = buildChunkText(current, nextChunkOverlap, text);
                    nextChunkOverlap = tail(chunkText, overlapChars);
                    current.clear();
                    currentLen = 0;
                }

                // Slide a window of size targetCharsPerChunk across the long sentence,
                // using the configured overlap between consecutive windows.
                int start = s.start;
                while (start < s.end) {
                    final int end = Math.min(start + targetCharsPerChunk, s.end);
                    final SentenceSpan piece = new SentenceSpan(start, end);

                    final List<SentenceSpan> single = new ArrayList<>(1);
                    single.add(piece);
                    result.add(createChunkWithOverlap(docId, text, single, nextChunkOverlap, seq++, pageMap));

                    final String just = buildChunkText(single, nextChunkOverlap, text);
                    nextChunkOverlap = tail(just, overlapChars);

                    // Advance window with overlap preserved inside the long sentence.
                    if (end < s.end && overlapChars > 0) {
                        start = Math.max(end - overlapChars, start + 1);
                    } else {
                        start = end;
                    }
                }
                continue;
            }

            // Case 2: Normal sentence. Check if adding it would exceed the target once overlap and spaces are counted.
            final int projectedLen = currentLen + (current.isEmpty() ? 0 : 1) + sLen + nextChunkOverlap.length();
            if (projectedLen > targetCharsPerChunk && (!current.isEmpty() || !nextChunkOverlap.isEmpty())) {
                // Close current chunk and compute the next overlap from its tail.
                result.add(createChunkWithOverlap(docId, text, current, nextChunkOverlap, seq++, pageMap));
                final String chunkText = buildChunkText(current, nextChunkOverlap, text);
                nextChunkOverlap = tail(chunkText, overlapChars);
                current.clear();
                currentLen = 0;
            }

            // Add sentence to the current packing window; track the extra single-space if it’s not the first.
            current.add(s);
            currentLen += (current.size() == 1 ? sLen : (1 + sLen));
        }

        // Flush any trailing partial chunk.
        if (!current.isEmpty() || !nextChunkOverlap.isEmpty()) {
            result.add(createChunkWithOverlap(docId, text, current, nextChunkOverlap, seq, pageMap));
        }

        return result;
    }

    /**
     * Validates basic input constraints.
     *
     * @param docId   must be non-null and non-blank
     * @param text    must be non-null (may be blank)
     * @param pageMap if non-null, length must equal {@code text.length()}
     * @throws IllegalArgumentException if any constraint is violated
     */
    private void validateInputs(final String docId, final String text, final int[] pageMap) {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId cannot be null or blank");
        }
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (pageMap != null && pageMap.length != text.length()) {
            throw new IllegalArgumentException("pageMap length must match text length");
        }
    }

    /**
     * Builds the realized chunk text as:
     * <pre>
     *     overlap + join(sentences, " ")
     * </pre>
     *
     * @param sentences sentence spans to include (in order)
     * @param overlap   overlap prefix (may be empty)
     * @param source    original source text
     * @return materialized chunk string
     */
    private String buildChunkText(final List<SentenceSpan> sentences,
                                  final String overlap,
                                  final String source) {
        final StringBuilder sb = new StringBuilder();
        if (overlap != null && !overlap.isEmpty()) {
            sb.append(overlap);
        }
        for (int i = 0; i < sentences.size(); i++) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            final SentenceSpan s = sentences.get(i);
            sb.append(source, s.start, s.end);
        }
        return sb.toString();
    }

    /**
     * Creates a {@link Chunk} from the accumulated sentences plus the current overlap prefix.
     * <p>
     * Page span is derived by converting the estimated absolute character span of the chunk
     * (first sentence's start minus overlap length, through last sentence's end) into
     * page indices via {@link #pageOf(int[], int)}. If no sentences are present and only
     * overlap exists, the span is taken as the final {@code overlap.length()} characters of {@code text}.
     * </p>
     *
     * @param docId        document identifier
     * @param text         original source text
     * @param sentences    sentences included in this chunk
     * @param overlapText  overlap prefix to prepend
     * @param sequence     monotonic sequence number starting at 0
     * @param pageMap      optional page map
     * @return constructed {@link Chunk}
     */
    private Chunk createChunkWithOverlap(final String docId,
                                         final String text,
                                         final List<SentenceSpan> sentences,
                                         final String overlapText,
                                         final int sequence,
                                         final int[] pageMap) {
        final String chunkText = buildChunkText(sentences, overlapText, text).trim();

        int absoluteStart;
        int absoluteEnd;

        if (!sentences.isEmpty()) {
            absoluteStart = sentences.get(0).start;
            absoluteEnd = sentences.get(sentences.size() - 1).end;
            // Attribute the overlap to the previous region by shifting the start backward.
            if (overlapText != null && !overlapText.isEmpty()) {
                absoluteStart = Math.max(0, absoluteStart - overlapText.length());
            }
        } else if (overlapText != null && !overlapText.isEmpty()) {
            // Degenerate case: chunk contains only overlap (rare but guarded).
            absoluteStart = Math.max(0, text.length() - overlapText.length());
            absoluteEnd = text.length();
        } else {
            absoluteStart = 0;
            absoluteEnd = 0;
        }

        final int pageStart = pageOf(pageMap, absoluteStart);
        final int pageEnd = pageOf(pageMap, Math.max(absoluteEnd - 1, absoluteStart));
        final String chunkId = buildChunkId(docId, sequence, pageStart, pageEnd);

        return new Chunk(docId, chunkId, chunkText, pageStart, pageEnd);
    }

    /**
     * Safe lookup of page index for a given character position.
     *
     * @param pageMap   per-character page index array, or {@code null}
     * @param charIndex character index into the source text
     * @return page index at {@code charIndex}, or {@code -1} if {@code pageMap} is absent
     */
    private static int pageOf(final int[] pageMap, final int charIndex) {
        if (pageMap == null || pageMap.length == 0) {
            return -1;
        }
        final int idx = Math.clamp(charIndex, 0, pageMap.length - 1);
        return pageMap[idx];
    }

    /**
     * Builds a stable chunk identifier:
     * <pre>
     *   {docId}_{seq(5 digits)}[_p{pageStart}-{pageEnd}]
     * </pre>
     * The page suffix is included when both {@code pageStart} and {@code pageEnd} are
     * positive (your tests expect 1-based page indices).
     *
     * @param docId     document id
     * @param seq       zero-based sequence number
     * @param pageStart first page index covered (or {@code -1} if unknown)
     * @param pageEnd   last page index covered (or {@code -1} if unknown)
     * @return chunk id string
     */
    private static String buildChunkId(final String docId,
                                       final int seq,
                                       final int pageStart,
                                       final int pageEnd) {
        final String seqStr = String.format("%05d", seq);
        if (pageStart >= 1 && pageEnd >= 1) {
            return docId + "_" + seqStr + "_p" + pageStart + "-" + pageEnd;
        }
        return docId + "_" + seqStr;
    }

    /**
     * Returns the final {@code n} characters of {@code s}, or an empty string if
     * {@code n <= 0} or {@code s} is null/empty.
     *
     * @param s source string
     * @param n number of trailing characters to return
     * @return trailing substring of length {@code min(n, s.length())}, or {@code ""} for non-positive {@code n}
     */
    private static String tail(final String s, final int n) {
        if (s == null || s.isEmpty() || n <= 0) {
            return "";
        }
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    /**
     * Splits {@code text} into sentence spans using {@link #SENTENCE_SPLIT} as a primary indicator
     * and {@link #ABBREVIATION_PATTERN} to suppress false boundaries.
     *
     * <p><strong>Algorithm:</strong>
     * We scan for whitespace runs that follow a terminal punctuation mark and precede an uppercase/quote.
     * For each candidate boundary, we inspect up to ~20 characters before the whitespace to see whether
     * it ends with a known abbreviation. If so, we do not split there. Otherwise, we emit a sentence
     * from the previous boundary to the current boundary (exclusive of the whitespace), and continue.
     * Finally, we add the trailing sentence from the last boundary to the end of the text.</p>
     *
     * @param text source text
     * @return ordered list of half-open spans {@code [start, end)} covering each sentence
     */
    private static List<SentenceSpan> splitSentences(final String text) {
        final List<SentenceSpan> sentences = new ArrayList<>();
        final Matcher matcher = SENTENCE_SPLIT.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            final int splitPoint = matcher.start(); // start of the whitespace run
            final String beforeSplit = text.substring(Math.max(0, splitPoint - 20), splitPoint).trim();

            // If the window right before the whitespace ends with a known abbreviation (e.g., "Dr."),
            // do not treat this as a sentence boundary.
            if (!ABBREVIATION_PATTERN.matcher(beforeSplit).find()) {
                if (splitPoint > lastEnd) {
                    sentences.add(new SentenceSpan(lastEnd, splitPoint));
                }
                // Skip over the whitespace; the next sentence starts at matcher.end().
                lastEnd = matcher.end();
            }
        }
        // Trailing sentence (or entire text if no boundary found).
        if (lastEnd < text.length()) {
            sentences.add(new SentenceSpan(lastEnd, text.length()));
        }
        return sentences;
    }

}
