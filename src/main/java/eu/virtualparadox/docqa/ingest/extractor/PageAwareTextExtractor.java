package eu.virtualparadox.docqa.ingest.extractor;

import eu.virtualparadox.docqa.ingest.chunker.Chunker;
import eu.virtualparadox.docqa.ingest.cleaner.CleaningResult;
import eu.virtualparadox.docqa.ingest.cleaner.TextCleaner;
import eu.virtualparadox.docqa.ingest.model.Chunk;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF-specific extractor that uses Apache PDFBox to produce:
 * <ul>
 *   <li>a continuous String concatenating all pages in order, and</li>
 *   <li>a per-character {@code int[]} page map (1-based), aligned to the text length.</li>
 * </ul>
 * <p>This enables downstream chunkers to preserve sentence continuity across pages
 * while still computing accurate page spans for citations.</p>
 */
@Service
@Primary
@RequiredArgsConstructor
public final class PageAwareTextExtractor implements TextExtractor {

    private final Chunker chunker;
    private final TextCleaner textCleaner;

    @Override
    public List<Chunk> extractText(final String id, final Path path) {
        try (PDDocument pdf = PDDocument.load(path.toFile())) {
            final int pageCount = pdf.getNumberOfPages();
            final PDFTextStripper stripper = new PDFTextStripper();

            final StringBuilder rawText = new StringBuilder(100_000);
            final List<Integer> rawPageMap = new ArrayList<>(100_000);

            // Extract raw text with page mapping
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);

                final String pageTextRaw = stripper.getText(pdf);
                final String pageText = Normalizer.normalize(pageTextRaw, Normalizer.Form.NFC);

                rawText.append(pageText);

                final int len = pageText.length();
                for (int i = 0; i < len; i++) {
                    rawPageMap.add(page);
                }
            }

            // Convert to array for TextCleaner
            final int[] pageMapArray = rawPageMap.stream().mapToInt(Integer::intValue).toArray();

            // Apply cleaning while preserving page mapping
            final CleaningResult cleaningResult = textCleaner.cleanTextWithPageMapping(
                    rawText.toString(),
                    pageMapArray
            );

            return chunker.sentenceAwareChunks(id, cleaningResult.getCleanText(), cleaningResult.getPageMap());
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to extract text from PDF", e);
        }
    }
}