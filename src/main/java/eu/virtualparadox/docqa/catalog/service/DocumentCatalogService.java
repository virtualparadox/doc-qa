package eu.virtualparadox.docqa.catalog.service;

import eu.virtualparadox.docqa.catalog.entity.DocumentEntity;
import eu.virtualparadox.docqa.catalog.EDocumentStatus;
import eu.virtualparadox.docqa.catalog.repo.DocumentRepository;
import eu.virtualparadox.docqa.application.config.ApplicationConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer responsible for managing the document catalog.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Persisting uploaded documents to the filesystem under {@code ~/.docqa/blobs}</li>
 *     <li>Maintaining metadata records in the relational catalog (H2)</li>
 *     <li>Providing access to catalog records for listing, lookup, and deletion</li>
 *     <li>Delegating indexing and embedding steps to other services (not handled here)</li>
 * </ul>
 *
 * <p>This reindex guarantees that metadata and blob storage remain in sync.
 * Vector indexing (Lucene HNSW) will be handled separately, with the {@code docId} acting as the link.</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentCatalogService {

    /** Default embedding model placeholder until actual embedding occurs. */
    private static final String DEFAULT_EMBED_MODEL = "unknown";

    /** Fallback MIME type if detection fails. */
    private static final String DEFAULT_MIME = "application/octet-stream";

    /** Temporary file prefix for atomic uploads. */
    private static final String TEMP_FILE_PREFIX = "up-";

    /** Temporary file suffix for atomic uploads. */
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private final DocumentRepository repository;
    private final ApplicationConfig props;

    /**
     * Saves an uploaded document into the catalog and blob storage.
     *
     * <p>Steps performed:
     * <ol>
     *     <li>Generates a new unique identifier for the document</li>
     *     <li>Determines file extension and target path in {@code ~/.docqa/blobs}</li>
     *     <li>Writes the uploaded content to disk atomically</li>
     *     <li>Detects MIME type if not explicitly provided</li>
     *     <li>Persists metadata in the relational database</li>
     * </ol>
     *
     * @param originalFilename the original filename provided by the user
     * @param mime             the MIME type, may be {@code null} or blank
     * @param content          input stream with the document bytes (caller is responsible for closing)
     * @return the persisted {@link DocumentEntity}
     * @throws IOException if writing to the filesystem fails
     */
    @Transactional
    public DocumentEntity save(final String originalFilename,
                               final String mime,
                               final InputStream content,
                               final EDocumentStatus status) throws IOException {

        final String id = generateId();
        final String ext = fileExtension(originalFilename);

        final Path blobsDir = props.getBlob();
        Files.createDirectories(blobsDir);

        final Path target = blobsDir.resolve(id + ext);
        final Path temp = Files.createTempFile(blobsDir, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);

        final long bytes = Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        final String detectedMime;
        if (mime == null || mime.isBlank()) {
            final String probed = Files.probeContentType(target);
            detectedMime = probed != null ? probed : DEFAULT_MIME;
        } else {
            detectedMime = mime;
        }

        final DocumentEntity entity = DocumentEntity.builder()
                .id(id)
                .title(originalFilename)
                .mime(detectedMime)
                .sizeBytes(bytes)
                .chunks(0)
                .embedModel(DEFAULT_EMBED_MODEL)
                .addedAt(Instant.now())
                .lastIndexedAt(null)
                .blobPath(target)
                .status(status)
                .build();

        return repository.save(entity);
    }

    /**
     * Lists all documents currently in the catalog.
     *
     * @return list of all {@link DocumentEntity} objects
     */
    @Transactional(readOnly = true)
    public List<DocumentEntity> listAll() {
        return repository.findAll();
    }

    /**
     * Retrieves a document entity by its identifier.
     *
     * @param id the document identifier
     * @return an {@link Optional} containing the document if found
     */
    @Transactional(readOnly = true)
    public Optional<DocumentEntity> findById(final String id) {
        return repository.findById(id);
    }

    /**
     * Deletes a document from both catalog and blob storage.
     *
     * <p>Note: Vector index deletion should be performed separately.</p>
     *
     * @param id the document identifier
     * @throws IOException if deleting the blob file fails
     */
    @Transactional
    public void delete(final String id) throws IOException {
        final Optional<DocumentEntity> entityOpt = repository.findById(id);
        if (entityOpt.isPresent()) {
            final DocumentEntity doc = entityOpt.get();
            if (doc.getBlobPath() != null) {
                Files.deleteIfExists(doc.getBlobPath());
            }
            repository.deleteById(id);
        }
    }

    @Transactional
    public void updateStatus(final DocumentEntity doc, final EDocumentStatus status) {
        repository.updateStatus(doc.getId(), status);
    }

    @Transactional
    public void updateChunksAndStatus(final DocumentEntity doc, final int chunks, final EDocumentStatus status) {
        repository.updateChunksAndStatus(doc.getId(), chunks, status);
    }

    /**
     * Generates a new unique identifier for a document.
     * <p>
     * Currently uses a UUID (dashes removed) for simplicity. In the future,
     * this may be replaced with ULID/KSUID for lexicographic ordering.
     *
     * @return new document identifier as a String
     */
    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Extracts the file extension from a filename.
     *
     * @param name original filename, may be null
     * @return the extension including the dot (e.g., ".pdf"), or an empty string if none found
     */
    private String fileExtension(final String name) {
        if (name == null) {
            return "";
        }
        final int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return name.substring(dot).trim().toLowerCase(Locale.ROOT);
    }
}
