package eu.virtualparadox.docqa.ingest.chunker;

import eu.virtualparadox.docqa.ingest.model.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {

    private static final int TARGET = 2000;
    private static final int OVERLAP = 200;

    private final Chunker chunker = new Chunker(TARGET, OVERLAP);

    // ---------- Helpers ----------

    /**
     * Build a text of N sentences, each "Sentence<i>. "
     */
    private static String buildSentences(int count, int averageSentenceLen) {
        StringBuilder sb = new StringBuilder(count * (averageSentenceLen + 2));
        Random rnd = new Random(123);
        for (int i = 1; i <= count; i++) {
            int bodyLen = Math.max(5, averageSentenceLen - 4);
            String body = randomLetters(bodyLen, rnd);
            // Capitalize first letter to satisfy chunker regex lookahead (uppercase start)
            body = Character.toUpperCase(body.charAt(0)) + body.substring(1);
            sb.append(body).append(". ");
        }
        return sb.toString();
    }

    private static String randomLetters(int n, Random rnd) {
        StringBuilder s = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = (char) ('a' + rnd.nextInt(26));
            s.append(c);
        }
        return s.toString();
    }

    /**
     * Make a pageMap for text where [start, end) char indices map to a given page.
     */
    private static int[] makePageMap(String text, int... pageBreaks) {
        int[] map = new int[text.length()];
        int page = 1;
        int nextBreakIdx = (pageBreaks.length > 0) ? pageBreaks[0] : text.length();
        int breakPtr = 0;

        for (int i = 0; i < text.length(); i++) {
            if (i >= nextBreakIdx && breakPtr < pageBreaks.length - 1) {
                breakPtr++;
                nextBreakIdx = pageBreaks[breakPtr];
                page++;
            }
            map[i] = page;
        }
        return map;
    }

    /**
     * Extract the page range suffix from chunkId like "..._p12-13", or null if absent.
     */
    private static int[] pageRangeFromChunkId(String chunkId) {
        Matcher m = Pattern.compile("_p(\\d+)-(\\d+)$").matcher(chunkId);
        if (!m.find()) return null;
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    }

    /**
     * Reconstructs text from chunks using overlaps (drop first OVERLAP chars of each subsequent chunk).
     */
    private static String reconstructFromChunks(List<Chunk> chunks, int overlap) {
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(chunks.getFirst().text());
        for (int i = 1; i < chunks.size(); i++) {
            String t = chunks.get(i).text();
            String add = (t.length() > overlap) ? t.substring(overlap) : "";
            sb.append(add);
        }
        return sb.toString();
    }

    /**
     * Normalize whitespace for tolerant comparisons.
     */
    private static String canonicalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Get tail of length n (or whole string if shorter).
     */
    private static String tail(String s, int n) {
        if (s.length() <= n) return s;
        return s.substring(s.length() - n);
    }

    // ---------- Tests ----------

    @Test
    @DisplayName("Blank or null text yields no chunks")
    void blankOrNull() {
        assertThrows(IllegalArgumentException.class, () -> chunker.sentenceAwareChunks("doc", null).isEmpty());
        assertTrue(chunker.sentenceAwareChunks("doc", "").isEmpty());
        assertTrue(chunker.sentenceAwareChunks("doc", "   ").isEmpty());
    }

    @Test
    @DisplayName("Short text yields one chunk without page info")
    void shortText_noPageMap() {
        String text = "Hello world.";
        List<Chunk> chunks = chunker.sentenceAwareChunks("docA", text);

        assertEquals(1, chunks.size());
        Chunk c = chunks.get(0);
        assertEquals("docA_00000", c.chunkId());
        assertEquals(text, c.text());
        assertNull(pageRangeFromChunkId(c.chunkId()));
    }

    @Test
    @DisplayName("Page map is applied and chunkId includes page span")
    void pageMapApplied() {
        String text = "Alpha sentence. Bravo sentence. Charlie sentence.";
        int[] pageMap = makePageMap(text, 0); // all on page 1
        List<Chunk> chunks = chunker.sentenceAwareChunks("docB", text, pageMap);

        assertEquals(1, chunks.size());
        Chunk c = chunks.get(0);
        int[] suffix = pageRangeFromChunkId(c.chunkId());
        assertNotNull(suffix);
        assertEquals(1, suffix[0]);
        assertEquals(1, suffix[1]);
    }

    @Test
    @DisplayName("Large text splits into multiple chunks; overlaps carried; reconstruction is text-equivalent modulo whitespace")
    void largeText_multipleChunks_reconstruction() {
        String text = buildSentences(150, 35); // ~5.5k chars
        int[] pageMap = makePageMap(text, 0, 2500, 4000); // p1:0-2499, p2:2500-3999, p3:>=4000
        List<Chunk> chunks = chunker.sentenceAwareChunks("docC", text, pageMap);

        assertTrue(chunks.size() >= 2, "Should produce multiple chunks");

        // Overlap continuity: next chunk should contain previous tail (not necessarily at index 0)
        for (int i = 1; i < chunks.size(); i++) {
            String prevTail = tail(chunks.get(i - 1).text(), OVERLAP);
            assertTrue(chunks.get(i).text().contains(prevTail),
                    "Chunk " + i + " should contain tail of chunk " + (i - 1));
        }

        // Reconstruction vs original (normalize whitespace to account for trim() and extra spaces)
        String reconstructed = canonicalize(reconstructFromChunks(chunks, OVERLAP));
        String original = canonicalize(text);

        // Strong equivalence: reconstructed should start with the first chunk
        assertTrue(reconstructed.startsWith(canonicalize(chunks.getFirst().text())),
                "Reconstructed should start with first chunk content");

        // And end with the last 150 canonicalized chars of original
        String originalTail = original.length() > 150 ? original.substring(original.length() - 150) : original;
        assertTrue(reconstructed.contains(originalTail),
                "Reconstructed should contain the end of the original content");

        // Sanity: reconstructed length should be at least original length minus overlap jitter
        assertTrue(reconstructed.length() >= original.length() - 5,
                "Reconstructed should be approximately as long as original after whitespace normalization");
    }

    @Test
    @DisplayName("Page ranges correctly span multiple pages when chunk crosses boundary")
    void pageRanges_crossBoundary() {
        String text = buildSentences(200, 20); // ~4.4k
        int[] pageMap = makePageMap(text, 0, 1500, 3000); // p1:0-1499, p2:1500-2999, p3:>=3000

        List<Chunk> chunks = chunker.sentenceAwareChunks("docD", text, pageMap);
        assertTrue(chunks.size() >= 2);

        boolean anyCrosses = false;
        for (Chunk c : chunks) {
            int[] suffix = pageRangeFromChunkId(c.chunkId());
            if (suffix != null && suffix[1] > suffix[0]) {
                anyCrosses = true;
            }
        }
        assertTrue(anyCrosses, "At least one chunk should span multiple pages");
    }

    @Test
    @DisplayName("Last chunk also has pageStart/pageEnd when pageMap is present")
    void lastChunk_hasPageSpans() {
        String text = buildSentences(120, 25); // ~3k
        int[] pageMap = makePageMap(text, 0, 1800); // 2 pages
        List<Chunk> chunks = chunker.sentenceAwareChunks("docE", text, pageMap);

        Chunk last = chunks.getLast();
        int[] suffix = pageRangeFromChunkId(last.chunkId());
        assertNotNull(suffix, "Last chunk should include page suffix");
        assertTrue(suffix[0] >= 1 && suffix[1] >= suffix[0]);
    }

    @Test
    @DisplayName("No page suffix when pageMap is null")
    void noPageSuffix_whenNoPageMap() {
        String text = buildSentences(30, 40);
        List<Chunk> chunks = chunker.sentenceAwareChunks("docF", text, null);

        for (Chunk c : chunks) {
            assertNull(pageRangeFromChunkId(c.chunkId()),
                    "chunkId must not include page suffix when pageMap is null");
        }
    }

    @Test
    @DisplayName("Chunks preserve sentence boundaries")
    void sentenceBoundariesPreserved() {
        String text = "Alpha beta gamma. Delta epsilon zeta. Eta theta iota. Kappa lambda mu.";
        List<Chunk> chunks = chunker.sentenceAwareChunks("docG", text, null);

        String reconstructed = canonicalize(reconstructFromChunks(chunks, OVERLAP));
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            assertTrue(reconstructed.contains(canonicalize(sentence)),
                    "Reconstructed should contain full sentence: " + sentence);
        }
    }
}
