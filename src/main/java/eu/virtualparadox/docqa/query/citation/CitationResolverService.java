package eu.virtualparadox.docqa.query.citation;

import eu.virtualparadox.docqa.catalog.entity.DocumentEntity;
import eu.virtualparadox.docqa.catalog.service.DocumentCatalogService;
import eu.virtualparadox.docqa.query.citation.pageinterval.PageInterval;
import eu.virtualparadox.docqa.query.citation.pageinterval.PageIntervalMerger;
import eu.virtualparadox.docqa.rag.rerank.model.RerankResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service responsible for producing human-friendly {@link Citation} objects from
 * low-level RAG reranking results. The process:
 * <ol>
 *   <li>Group all page intervals by document id.</li>
 *   <li>Merge overlapping/touching intervals per document via {@link PageIntervalMerger}.</li>
 *   <li>Map each document id to its {@link DocumentEntity} metadata and build {@link Citation}s.</li>
 * </ol>
 *
 * <p><strong>Ordering:</strong> Citations are returned in the order the documents
 * are first encountered in {@code rerankResults} (stable, first-seen order).</p>
 *
 * <p><strong>Thread-safety:</strong> This class is stateless and thus thread-safe
 * under typical Spring usage.</p>
 */
@Service
@RequiredArgsConstructor
public final class CitationResolverService {

    private final DocumentCatalogService documentCatalogService;
    private final PageIntervalMerger pageIntervalMerger;

    /**
     * Builds a list of {@link Citation}s from the provided reranking results.
     * <p>
     * For each {@link RerankResult} this method:
     * <ul>
     *   <li>Resolves the associated {@link DocumentEntity} via {@link DocumentCatalogService}.</li>
     *   <li>Collects its page span as a {@link PageInterval}.</li>
     *   <li>Merges all intervals per document to produce compact ranges.</li>
     * </ul>
     *
     * <p><strong>Complexity:</strong> Let {@code n} be the number of results and
     * {@code k_i} the number of intervals per document {@code i}. Grouping is {@code O(n)}.
     * Merging is typically {@code O(k_i log k_i)} per document (depending on the merger implementation).
     * Overall, this is efficient for typical RAG top-k pipelines.</p>
     *
     * @param rerankResults non-null list of reranked retrieval results; entries must not be {@code null}
     * @return an unmodifiable list of {@link Citation}s, ordered by the first appearance of their document
     *         in {@code rerankResults}; never {@code null}
     * @throws NoSuchElementException  if a {@link DocumentEntity} referenced by a result cannot be found
     * @throws IllegalArgumentException if a result has an invalid page range (e.g., fromPage &gt; toPage)
     * @throws NullPointerException     if {@code rerankResults} or any of its elements is {@code null}
     */
    public List<Citation> getCitations(final List<RerankResult> rerankResults) {
        Objects.requireNonNull(rerankResults, "rerankResults must not be null");

        if (rerankResults.isEmpty()) {
            return Collections.emptyList();
        }

        // Preserve first-seen document order using LinkedHashMap.
        final Map<String, List<PageInterval>> intervalsByDocumentId = new LinkedHashMap<>();
        final Map<String, DocumentEntity> documentsByDocumentId = new LinkedHashMap<>();

        for (final RerankResult rerankResult : rerankResults) {
            Objects.requireNonNull(rerankResult, "rerankResults must not contain null elements");

            final String docId = rerankResult.docId();
            final Optional<DocumentEntity> maybeDocument = documentCatalogService.findById(docId);
            if (maybeDocument.isEmpty()) {
                throw new NoSuchElementException("Document not found: " + docId);
            }

            final int fromPage = rerankResult.fromPage();
            final int toPage = rerankResult.toPage();
            if (fromPage > toPage) {
                throw new IllegalArgumentException("Invalid page interval: fromPage (" + fromPage + ") > toPage (" + toPage + ") for document " + docId);
            }

            final DocumentEntity documentEntity = maybeDocument.get();
            documentsByDocumentId.putIfAbsent(documentEntity.getId(), documentEntity);

            final PageInterval pageInterval = new PageInterval(fromPage, toPage);
            intervalsByDocumentId
                    .computeIfAbsent(documentEntity.getId(), k -> new ArrayList<>())
                    .add(pageInterval);
        }

        // Merge intervals per document.
        final Map<String, List<PageInterval>> mergedIntervalsByDocumentId = new LinkedHashMap<>();
        for (final Map.Entry<String, List<PageInterval>> entry : intervalsByDocumentId.entrySet()) {
            final String documentId = entry.getKey();
            final List<PageInterval> intervals = entry.getValue();
            final List<PageInterval> merged = pageIntervalMerger.merge(intervals);
            mergedIntervalsByDocumentId.put(documentId, merged);
        }

        // Build citations preserving first-seen order.
        final List<Citation> result = new ArrayList<>(mergedIntervalsByDocumentId.size());
        for (final Map.Entry<String, List<PageInterval>> entry : mergedIntervalsByDocumentId.entrySet()) {
            final String documentId = entry.getKey();
            final DocumentEntity documentEntity = documentsByDocumentId.get(documentId);
            if (documentEntity == null) {
                throw new IllegalStateException("Document entity missing for ID: " + documentId);
            }

            final List<PageInterval> intervals = entry.getValue();
            final Citation citation = new Citation(documentEntity.getId(), documentEntity.getTitle(), intervals);
            result.add(citation);
        }

        return Collections.unmodifiableList(result);
    }
}
