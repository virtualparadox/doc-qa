package eu.virtualparadox.docqa.rag.index;

import eu.virtualparadox.docqa.ingest.model.Chunk;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SearcherManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static eu.virtualparadox.docqa.util.LuceneConstants.*;

/**
 * Lucene-backed implementation of {@link VectorIndexService} using the HNSW k-NN graph.
 * <p>
 * This reindex writes chunk text and dense vectors into a single Lucene index:
 * <ul>
 *   <li>Each chunk is stored as one Lucene {@link Document}</li>
 *   <li>Text content is stored for later answer synthesis and citations</li>
 *   <li>Vectors are written via {@link KnnFloatVectorField} to enable fast ANN search</li>
 * </ul>
 *
 * <h3>Field layout</h3>
 * <ul>
 *   <li>{@code docId} – {@link StringField#TYPE_STORED}: document identifier for grouping/deletion</li>
 *   <li>{@code chunkId} – {@link StringField#TYPE_STORED}: unique chunk identifier</li>
 *   <li>{@code text} – {@link TextField}: full chunk text, also {@link StoredField} to retrieve</li>
 *   <li>{@code vector} – {@link KnnFloatVectorField}: dense float vector (HNSW indexed)</li>
 * </ul>
 *
 * <p><b>Vector dimensions:</b> Lucene requires a constant dimension per vector field across an index.
 * This implementation validates incoming vectors are consistent. If your pipeline changes the embedding
 * model (dimension), reindex into a fresh index directory or perform a controlled migration.</p>
 */
@Service
@RequiredArgsConstructor
public final class LuceneVectorIndexService implements VectorIndexService {

    private final IndexWriter writer;
    private final SearcherManager searcherManager;

    /**
     * In-memory notion of the configured vector dimension for this index instance.
     * <p>
     * Lucene enforces a single dimension per vector field name across the entire index.
     * We cache the first-seen dimension and validate subsequent inserts.
     */
    private Integer vectorDim;

    /**
     * Adds or replaces all chunks for a given document.
     * <p>
     * The operation is implemented as:
     * <ol>
     *   <li>Delete any existing chunks for the {@code docId}</li>
     *   <li>Insert the provided {@code chunks}+{@code vectors} pairs</li>
     *   <li>Commit and refresh the searcher for near-real-time visibility</li>
     * </ol>
     *
     * @param docId   parent document identifier
     * @param chunks  chunk metadata (size must match {@code vectors})
     * @param vectors dense vectors, one per chunk, row-major (all same dimension)
     * @throws IOException if writing to the Lucene index fails
     * @throws IllegalArgumentException if input lists are null, empty, or size/dimension mismatch
     */
    @Override
    public void upsert(final String docId,
                       final List<Chunk> chunks,
                       final List<float[]> vectors) throws IOException {

        requireNonNullOrEmpty(docId, "docId");
        requireNonNullOrEmpty(chunks, "chunks");
        requireNonNullOrEmpty(vectors, "vectors");


        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks.size() != vectors.size()");
        }

        // Validate & cache vector dimension
        final int dim = vectors.getFirst().length;
        ensureConsistentDimension(dim);
        for (final float[] v : vectors) {
            if (v == null || v.length != dim) {
                throw new IllegalArgumentException("All vectors must be non-null and of length " + dim);
            }
        }

        // 1) delete previous chunks for this doc
        writer.deleteDocuments(new Term(FIELD_DOC_ID, docId));

        // 2) add new chunks
        for (int i = 0; i < chunks.size(); i++) {
            final Chunk c = chunks.get(i);
            final float[] vec = vectors.get(i);
            writer.addDocument(buildLuceneDocument(docId, c, vec));
        }

        // 3) commit and refresh for NRT visibility
        writer.commit();
        searcherManager.maybeRefreshBlocking();
    }

    /**
     * Deletes all chunks associated with the given {@code docId}.
     *
     * @param docId parent document identifier
     * @throws IOException if index update fails
     */
    @Override
    public void deleteByDocId(final String docId) throws IOException {
        requireNonNullOrEmpty(docId, "docId");
        writer.deleteDocuments(new Term(FIELD_DOC_ID, docId));
        writer.commit();
        searcherManager.maybeRefreshBlocking();
    }

    /**
     * Ensures an internal, stable notion of the vector dimension.
     *
     * @param dim proposed dimension
     * @throws IllegalArgumentException if a different dimension has already been established
     */
    private void ensureConsistentDimension(final int dim) {
        if (dim <= 0) {
            throw new IllegalArgumentException("Vector dimension must be > 0");
        }
        if (vectorDim == null) {
            // first time we see vectors; cache the dimension
            vectorDim = dim;
        } else if (!vectorDim.equals(dim)) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch. Existing=" + vectorDim + ", new=" + dim +
                            " (reindex into a fresh index if you changed the embedder)");
        }
    }

    /**
     * Builds a Lucene {@link Document} for a single chunk+vector pair.
     *
     * @param docId parent document id
     * @param c     chunk payload
     * @param vec   dense float vector
     * @return a fully populated Lucene document
     */
    private Document buildLuceneDocument(final String docId,
                                         final Chunk c,
                                         final float[] vec) {
        final Document d = new Document();

        // Identifiers
        d.add(new StringField(FIELD_DOC_ID, docId, Field.Store.YES));
        d.add(new StringField(FIELD_CHUNK_ID, c.chunkId(), Field.Store.YES));

        // Text content (indexed + stored for retrieval)
        d.add(new TextField(FIELD_TEXT, c.text(), Field.Store.YES));

        // Vector for HNSW ANN search
        d.add(new KnnFloatVectorField(FIELD_VECTOR, vec));

        // Page range (stored only)
        d.add(new StoredField(FIELD_FROM_PAGE, c.pageStart()));
        d.add(new StoredField(FIELD_TO_PAGE, c.pageEnd()));

        return d;
    }

    /**
     * Utility to assert a required string or collection is non-null/non-empty.
     *
     * @param value value to check
     * @param name  parameter name for error messaging
     */
    private void requireNonNullOrEmpty(final Object value, final String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }

        if (value instanceof String s && s.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        if (value instanceof List<?> list && list.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
