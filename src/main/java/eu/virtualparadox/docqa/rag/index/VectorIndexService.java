package eu.virtualparadox.docqa.rag.index;

import eu.virtualparadox.docqa.ingest.model.Chunk;

import java.io.IOException;
import java.util.List;

/**
 * Abstraction over the vector index used for approximate nearest neighbor (ANN) search.
 * <p>
 * Implementations persist chunk-level vectors alongside the chunk text and identifiers,
 * and provide lifecycle operations required by the ingestion pipeline:
 * <ul>
 *   <li><b>Upsert</b> — add or replace all chunks for a document in one operation</li>
 *   <li><b>Delete</b> — remove all chunks belonging to a document</li>
 * </ul>
 * <p>
 * Notes:
 * <ul>
 *   <li>All vectors supplied to {@link #upsert(String, List, List)} MUST have the same dimension.</li>
 *   <li>Dimension must remain consistent across the entire index lifetime for a given vector field.</li>
 * </ul>
 */
public interface VectorIndexService {

    /**
     * Adds or replaces the indexed representation of all chunks for a document.
     * <p>
     * Implementations should treat this as a transactional unit:
     * delete existing chunks for {@code docId}, insert the new chunk+vector pairs,
     * and make the changes visible to search in a near‑real‑time manner.
     *
     * @param docId   the parent document identifier (non-null, non-blank)
     * @param chunks  ordered list of chunk metadata (non-null, non-empty)
     * @param vectors list of dense vectors, one per chunk, same order and dimension (non-null, non-empty)
     * @throws IOException              if writing to the underlying index fails
     * @throws IllegalArgumentException if parameters are null/empty or sizes/dimensions mismatch
     */
    void upsert(final String docId,
                final List<Chunk> chunks,
                final List<float[]> vectors) throws IOException;

    /**
     * Removes all indexed chunks belonging to the specified document.
     *
     * @param docId the parent document identifier (non-null, non-blank)
     * @throws IOException if the underlying index update fails
     */
    void deleteByDocId(final String docId) throws IOException;
}
