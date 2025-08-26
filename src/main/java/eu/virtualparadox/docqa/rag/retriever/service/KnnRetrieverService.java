package eu.virtualparadox.docqa.rag.retriever.service;

import eu.virtualparadox.docqa.rag.embed.EmbeddingService;
import eu.virtualparadox.docqa.rag.retriever.model.SearchResult;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static eu.virtualparadox.docqa.util.LuceneConstants.*;

/**
 * Provides semantic query capabilities over the Lucene HNSW index.
 * <p>
 * Steps:
 * <ol>
 *   <li>Embed the user query using {@link EmbeddingService}</li>
 *   <li>Run an ANN search with {@link KnnFloatVectorQuery}</li>
 *   <li>Convert the Lucene {@link TopDocs} into {@link SearchResult} DTOs</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public final class KnnRetrieverService implements RetrieverService {


    private final EmbeddingService embeddingService;
    private final SearcherManager searcherManager;

    /**
     * Executes a semantic search against the index.
     *
     * @param query user input string
     * @param k     maximum number of results to return
     * @return list of {@link SearchResult} objects (never null)
     * @throws IOException if the Lucene searcher cannot be acquired or executed
     */
    public List<SearchResult> search(final String query, final int k) throws IOException {
        final float[] vector = embeddingService.embedQuery(query);

        final IndexSearcher searcher = searcherManager.acquire();

        final KnnFloatVectorQuery knn = new KnnFloatVectorQuery(FIELD_VECTOR, vector, k);
        final TopDocs topDocs = searcher.search(knn, k);

        final List<SearchResult> results = new ArrayList<>();
        for (final ScoreDoc sd : topDocs.scoreDocs) {
            final Document doc = searcher.doc(sd.doc);
            results.add(new SearchResult(
                    doc.get(FIELD_DOC_ID),
                    doc.get(FIELD_CHUNK_ID),
                    doc.get(FIELD_TEXT),
                    doc.getField(FIELD_FROM_PAGE).numericValue().intValue(),
                    doc.getField(FIELD_TO_PAGE).numericValue().intValue(),
                    sd.score
            ));
        }
        return results;
    }
}
