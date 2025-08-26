package eu.virtualparadox.docqa.ingest.extractor;

import eu.virtualparadox.docqa.ingest.chunker.Chunker;
import eu.virtualparadox.docqa.ingest.cleaner.TextCleaner;
import eu.virtualparadox.docqa.ingest.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for PageAwareTextExtractor using a real PDF file.
 * This test uses the actual chunker implementation for realistic end-to-end testing.
 */
class PageAwareTextExtractorTest {

    private static final int TARGET_CHARS = 2000;
    private static final int OVERLAP_CHARS = 200;

    private PageAwareTextExtractor extractor;
    private Path testPdfPath;

    @BeforeEach
    void setUp() {
        // Create real chunker instance
        final Chunker chunker = new Chunker(TARGET_CHARS, OVERLAP_CHARS);
        final TextCleaner textCleaner = new TextCleaner();

        extractor = new PageAwareTextExtractor(chunker, textCleaner);

        // Look for output.pdf in test resources or current directory
        testPdfPath = findTestPdf();
        assertNotNull(testPdfPath, "Could not find test PDF file (output.pdf)");
        assertTrue(testPdfPath.toFile().exists(), "PDF file must exist: " + testPdfPath);
    }

    @Test
    @DisplayName("Extract and chunk real PDF produces valid results")
    void extractAndChunk_realPdf_producesValidResults() {
        // Execute the full extraction and chunking pipeline
        List<Chunk> chunks = extractor.extractText("ravenloft_test", testPdfPath);

        // Basic validation
        assertFalse(chunks.isEmpty(), "Should produce at least one chunk");

        // Detailed validation
        validateChunkStructure(chunks);
        validateChunkContent(chunks);
        validatePageInformation(chunks);
        validateOverlapBehavior(chunks);

        // Print summary for manual inspection
        printTestSummary(chunks);
    }

    @Test
    @DisplayName("Extracted chunks preserve document structure and readability")
    void extractedChunks_preserveDocumentStructure() {
        List<Chunk> chunks = extractor.extractText("structure_test", testPdfPath);

        // Test that chunks contain meaningful content
        for (Chunk chunk : chunks) {
            String text = chunk.text().trim();

            // Should contain actual words, not just whitespace/symbols
            long wordCount = text.toLowerCase().split("\\W+")
                    .length;
            assertTrue(wordCount >= 10,
                    "Chunk should contain at least 10 words: " + chunk.chunkId() + " (found " + wordCount + ")");

            // Should not be excessively short (unless it's the last chunk)
            if (!chunk.equals(chunks.get(chunks.size() - 1))) {
                assertTrue(text.length() >= 100,
                        "Non-final chunks should have substantial content: " + chunk.chunkId());
            }

            // Should not start or end with excessive whitespace
            assertFalse(text.startsWith("   "), "Chunk should not start with excessive whitespace");
            assertFalse(text.endsWith("   "), "Chunk should not end with excessive whitespace");
        }
    }


    @Test
    @DisplayName("Page information enables accurate citation")
    void pageInformation_enablesAccurateCitation() {
        List<Chunk> chunks = extractor.extractText("citation_test", testPdfPath);

        // Verify citation-ready information
        for (Chunk chunk : chunks) {
            // All chunks should have valid page information
            assertTrue(chunk.pageStart() >= 1, "Page start should be at least 1");
            assertTrue(chunk.pageEnd() >= chunk.pageStart(), "Page end should be >= page start");
            assertTrue(chunk.pageEnd() <= 11, "Page end should be within document range");

            // ChunkId should contain page information
            assertTrue(chunk.chunkId().contains("_p"),
                    "Chunk ID should contain page information: " + chunk.chunkId());
        }

        // Pages should progress logically through the document
        int previousMaxPage = 0;
        for (Chunk chunk : chunks) {
            assertTrue(chunk.pageEnd() >= previousMaxPage, "Chunks should progress through document pages [" + chunk.chunkId() + " vs. " + previousMaxPage + "]");
            previousMaxPage = chunk.pageEnd();
        }
    }

    @Test
    @DisplayName("Chunk overlaps maintain context continuity")
    void chunkOverlaps_maintainContextContinuity() {
        List<Chunk> chunks = extractor.extractText("overlap_test", testPdfPath);

        if (chunks.size() < 2) {
            return; // Skip test if document produces only one chunk
        }

        // Test overlap quality between consecutive chunks
        for (int i = 1; i < chunks.size(); i++) {
            String prevChunk = chunks.get(i - 1).text();
            String currentChunk = chunks.get(i).text();

            // Get potential overlap from end of previous chunk
            String prevTail = prevChunk.length() > OVERLAP_CHARS
                    ? prevChunk.substring(prevChunk.length() - OVERLAP_CHARS)
                    : prevChunk;

            // Normalize whitespace for comparison
            String normalizedTail = prevTail.replaceAll("\\s+", " ").trim();
            String normalizedCurrent = currentChunk.replaceAll("\\s+", " ").trim();

            // Current chunk should contain some portion of previous chunk's tail
            boolean hasOverlap = normalizedCurrent.contains(normalizedTail.substring(Math.max(0, normalizedTail.length() - 100)));

            assertTrue(hasOverlap,
                    String.format("Chunk %d should contain overlap from chunk %d", i, i - 1));
        }
    }

    @Test
    @DisplayName("Full document reconstruction maintains content integrity")
    void fullDocumentReconstruction_maintainsContentIntegrity() {
        List<Chunk> chunks = extractor.extractText("integrity_test", testPdfPath);

        // Reconstruct the document from chunks (removing overlaps)
        StringBuilder reconstructed = new StringBuilder();

        if (!chunks.isEmpty()) {
            // Add first chunk completely
            reconstructed.append(chunks.get(0).text());

            // Add subsequent chunks minus their overlap
            for (int i = 1; i < chunks.size(); i++) {
                String chunkText = chunks.get(i).text();
                // Simple overlap removal - take everything after first OVERLAP_CHARS
                String nonOverlapPortion = chunkText.length() > OVERLAP_CHARS
                        ? chunkText.substring(OVERLAP_CHARS)
                        : "";
                reconstructed.append(nonOverlapPortion);
            }
        }

        String reconstructedText = reconstructed.toString();

        // Basic integrity checks
        assertFalse(reconstructedText.isBlank(), "Reconstructed text should not be blank");
        assertTrue(reconstructedText.length() > 5000, "Should have substantial content for 10-page document");

        // Should contain typical gaming/narrative content indicators
        String lowerText = reconstructedText.toLowerCase();
        int contentIndicators = 0;
        if (lowerText.contains("character") || lowerText.contains("player")) contentIndicators++;
        if (lowerText.contains("spell") || lowerText.contains("magic")) contentIndicators++;
        if (lowerText.contains("damage") || lowerText.contains("attack")) contentIndicators++;
        if (lowerText.contains("level") || lowerText.contains("experience")) contentIndicators++;

        assertTrue(contentIndicators > 0, "Should contain typical RPG content keywords");
    }

    // Helper methods

    private Path findTestPdf() {
        // Try test resources first
        URL resource = getClass().getClassLoader().getResource("output.pdf");
        if (resource != null) {
            return Paths.get(resource.getPath());
        }

        // Try current directory
        Path currentDir = Paths.get("output.pdf");
        if (currentDir.toFile().exists()) {
            return currentDir;
        }

        // Try test directory
        Path testDir = Paths.get("src/test/resources/output.pdf");
        if (testDir.toFile().exists()) {
            return testDir;
        }

        // Try parent directory
        Path parentDir = Paths.get("../output.pdf");
        if (parentDir.toFile().exists()) {
            return parentDir;
        }

        return null;
    }

    private void validateChunkStructure(List<Chunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);

            // Basic structure
            assertNotNull(chunk.docId(), "Chunk should have document ID");
            assertNotNull(chunk.chunkId(), "Chunk should have chunk ID");
            assertNotNull(chunk.text(), "Chunk should have text");

            // ID format
            assertTrue(chunk.chunkId().matches(".*_\\d{5}(_p\\d+-\\d+)?"),
                    "Chunk ID should follow expected format: " + chunk.chunkId());

            // Sequence numbers should increment
            String expectedSeq = String.format("%05d", i);
            assertTrue(chunk.chunkId().contains(expectedSeq),
                    "Chunk should have correct sequence number: " + chunk.chunkId());
        }
    }

    private void validateChunkContent(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            String text = chunk.text();

            // Content validation
            assertFalse(text.isBlank(), "Chunk text should not be blank");
            assertTrue(text.length() <= TARGET_CHARS * 1.5,
                    "Chunk should not be excessively large: " + text.length());

            // Most chunks should be reasonably sized (except potentially the last one)
            if (!chunk.equals(chunks.get(chunks.size() - 1))) {
                assertTrue(text.length() >= TARGET_CHARS * 0.3,
                        "Non-final chunk should have reasonable minimum size");
            }
        }
    }

    private void validatePageInformation(List<Chunk> chunks) {
        Set<Integer> pagesSeen = new HashSet<>();

        for (Chunk chunk : chunks) {
            // Page numbers should be valid
            assertTrue(chunk.pageStart() >= 1, "Page start should be positive");
            assertTrue(chunk.pageEnd() >= chunk.pageStart(), "Page end should be >= page start");
            assertTrue(chunk.pageEnd() <= 11, "Page end should be within expected range");

            // Collect pages for coverage check
            for (int p = chunk.pageStart(); p <= chunk.pageEnd(); p++) {
                pagesSeen.add(p);
            }
        }

        // Should cover most pages of the document
        assertTrue(pagesSeen.size() >= 8, "Should cover most pages of 10-page document");
        assertTrue(pagesSeen.contains(1), "Should include first page");
    }

    private void validateOverlapBehavior(List<Chunk> chunks) {
        if (chunks.size() < 2) return;

        // Count chunks that properly overlap with their predecessor
        int overlappingChunks = 0;
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1).text();
            String current = chunks.get(i).text();

            // Check for some shared content (allowing for whitespace differences)
            String prevEnd = prev.substring(Math.max(0, prev.length() - OVERLAP_CHARS));
            if (current.contains(prevEnd.trim().substring(0, Math.min(50, prevEnd.trim().length())))) {
                overlappingChunks++;
            }
        }

        // Most chunk transitions should have some overlap
        assertTrue(overlappingChunks >= (chunks.size() - 1) * 0.7,
                "Most chunks should have meaningful overlap");
    }

    private void printTestSummary(List<Chunk> chunks) {
        System.out.println("\n=== INTEGRATION TEST SUMMARY ===");
        System.out.println("Document: " + testPdfPath.getFileName());
        System.out.println("Chunks produced: " + chunks.size());
        System.out.println("Target chunk size: " + TARGET_CHARS);
        System.out.println("Overlap size: " + OVERLAP_CHARS);

        int totalChars = chunks.stream().mapToInt(c -> c.text().length()).sum();
        System.out.println("Total characters: " + totalChars);

        if (!chunks.isEmpty()) {
            System.out.println("First chunk pages: " + chunks.getFirst().pageStart() + "-" + chunks.getFirst().pageEnd());
            System.out.println("Last chunk pages: " + chunks.getLast().pageStart() + "-" + chunks.getLast().pageEnd());
            System.out.println("Sample first chunk: " + chunks.getFirst().text().substring(0, Math.min(100, chunks.getFirst().text().length())) + "...");
        }
        System.out.println("=== END SUMMARY ===\n");
    }
}