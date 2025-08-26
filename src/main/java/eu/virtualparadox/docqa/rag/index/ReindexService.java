package eu.virtualparadox.docqa.rag.index;

import eu.virtualparadox.docqa.catalog.entity.DocumentEntity;
import eu.virtualparadox.docqa.catalog.repo.DocumentRepository;
import eu.virtualparadox.docqa.ingest.extractor.TextExtractor;
import eu.virtualparadox.docqa.rag.embed.EmbeddingService;
import eu.virtualparadox.docqa.ingest.model.Chunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the indexing pipeline for documents:
 * <ol>
 *     <li>Parse &amp; chunk original content (PDF/DOCX/HTML → normalized text blocks)</li>
 *     <li>Embed chunks to dense vectors (ONNX Runtime, later replacing any stub)</li>
 *     <li>Upsert chunks + vectors into the Lucene HNSW index</li>
 *     <li>Update catalog metadata (chunk count, embed model id, last indexed timestamp)</li>
 * </ol>
 * <p>
 * This reindex is intentionally thin and state-less; it composes dedicated services and
 * keeps the unit of work small (one document per call).
 */
@Service
@RequiredArgsConstructor
public class ReindexService {

    private final TextExtractor textExtractor;
    private final EmbeddingService embeddingService;
    private final VectorIndexService vectorIndexService;
    private final DocumentRepository documentRepository;

    /**
     * Reindexes a single document entity.
     * <p>
     * This method:
     * <ul>
     *     <li>Parses &amp; chunks the document</li>
     *     <li>Embeds chunks via {@link EmbeddingService}</li>
     *     <li>Upserts the chunk+vector pairs into Lucene</li>
     *     <li>Updates the catalog record (chunks, embedModel, lastIndexedAt)</li>
     * </ul>
     *
     * @param document the catalog entity to index
     * @throws Exception if any stage fails
     */
    @Transactional
    public void reindex(final DocumentEntity document) {
        try {
            // extract
            final List<Chunk> chunks = textExtractor.extractText(document.getId(), document.getBlobPath());

            // embed
            final var vectors = embeddingService.embed(chunks);

            // upsert into Lucene HNSW
            vectorIndexService.upsert(document.getId(), chunks, vectors);

            // update catalog record
            document.setChunks(chunks.size());
            document.setEmbedModel("");
            document.setLastIndexedAt(Instant.now());
            documentRepository.save(document);
        }
        catch (final Exception e) {
            throw new IllegalStateException("Reindex failed for document: " + document.getId(), e);
        }
    }
}
