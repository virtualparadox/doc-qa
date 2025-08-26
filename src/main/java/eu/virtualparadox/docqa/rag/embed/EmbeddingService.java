package eu.virtualparadox.docqa.rag.embed;

import eu.virtualparadox.docqa.ingest.model.Chunk;

import java.io.IOException;
import java.util.List;

/**
 * Computes dense vector embeddings for text chunks.
 */
public interface EmbeddingService {

    /**
     * Embeds the given chunks in batch.
     *
     * @param chunks list of chunks
     * @return list of float vectors (row-major), one per chunk
     * @throws Exception on failure
     */
    List<float[]> embed(List<Chunk> chunks) throws IOException;

    /**
     * Embeds a single query string into dense vector space.
     * <p>
     * Used at query time for semantic search.
     *
     * @param text the query string (non-null, non-blank)
     * @return a dense vector representation of the query
     */
    float[] embedQuery(final String text);
}
