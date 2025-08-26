package eu.virtualparadox.docqa.ingest.lifecycle;

import eu.virtualparadox.docqa.catalog.entity.DocumentEntity;
import eu.virtualparadox.docqa.catalog.EDocumentStatus;
import eu.virtualparadox.docqa.catalog.service.DocumentCatalogService;
import eu.virtualparadox.docqa.application.executor.IngestionExecutor;
import eu.virtualparadox.docqa.ingest.extractor.TextExtractor;
import eu.virtualparadox.docqa.rag.index.VectorIndexService;
import eu.virtualparadox.docqa.rag.index.ReindexService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/**
 * Manages the full lifecycle of documents:
 * <ul>
 *   <li>Catalog (database)</li>
 *   <li>Blob storage (filesystem)</li>
 *   <li>Vector index (Lucene)</li>
 * </ul>
 * Supports both synchronous and asynchronous ingestion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentLifecycleManager {

    private static final String ERROR_NOT_FOUND = "Document not found: ";

    private final DocumentCatalogService catalogService;
    private final TextExtractor textExtractor;
    private final VectorIndexService vectorIndexService;
    private final ReindexService reindexService;
    private final IngestionExecutor ingestionExecutor;
    private final DocumentProcessorTracker documentProcessorTracker;

    /**
     * Save + queue reindex asynchronously.
     * <p>If reindex fails, document + blob + index entries are removed.</p>
     */
    @Transactional
    public synchronized DocumentEntity saveAsync(final String fileName, final String mimeType, final InputStream is) throws Exception {
        final DocumentEntity saved = catalogService.save(fileName, mimeType, is, EDocumentStatus.QUEUED);
        final int chunkCount = textExtractor.extractText(saved.getId(), saved.getBlobPath()).size();
        documentProcessorTracker.addDocument(chunkCount);

        ingestionExecutor.submit(() -> {
            try {
                catalogService.updateChunksAndStatus(saved, chunkCount, EDocumentStatus.PROCESSING);
                log.info("Asynchronous reindex started for {}", saved.getId());

                reindexService.reindex(saved);

                saved.setStatus(EDocumentStatus.INDEXED);
                catalogService.updateStatus(saved, EDocumentStatus.INDEXED);

                log.info("Asynchronous reindex completed for {}", saved.getId());

            } catch (Exception e) {
                log.error("Asynchronous reindex failed, cleaning up {}", saved.getId(), e);
                try {
                    deleteDocument(saved.getId());
                } catch (IOException cleanupEx) {
                    log.error("Cleanup failed for {}", saved.getId(), cleanupEx);
                }
            }
        });

        return saved;
    }

    /**
     * Deletes a document and all associated artifacts.
     */
    @Transactional
    public void deleteDocument(final String id) throws IOException {
        final Optional<DocumentEntity> optDoc = catalogService.findById(id);
        if (optDoc.isEmpty()) {
            throw new IllegalArgumentException(ERROR_NOT_FOUND + id);
        }

        final DocumentEntity doc = optDoc.get();

        // 1. Delete blob
        if (doc.getBlobPath() != null) {
            try {
                Files.deleteIfExists(doc.getBlobPath());
                log.info("Deleted blob for document {} at {}", id, doc.getBlobPath());
            } catch (IOException e) {
                log.warn("Failed to delete blob for document {} at {}", id, doc.getBlobPath(), e);
            }
        }

        // 2. Delete from vector index
        vectorIndexService.deleteByDocId(id);

        // 3. Delete DB record
        catalogService.delete(id);

        log.info("Deleted document {} from catalog, blob storage, and index", id);
    }

    /**
     * List all documents in catalog.
     */
    public List<DocumentEntity> listAll() {
        return catalogService.listAll();
    }
}
