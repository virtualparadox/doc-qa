package eu.virtualparadox.docqa.rag.retriever.service;

import eu.virtualparadox.docqa.rag.embed.EmbeddingService;
import eu.virtualparadox.docqa.rag.retriever.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.*;

/**
 * Hybrid retriever: combines ANN semantic search (vector) with lexical BM25 keyword search.
 * <p>
 * Steps:
 * <ol>
 *   <li>Embed query and run ANN search with {@link KnnFloatVectorQuery}</li>
 *   <li>Parse query text and run BM25 keyword search</li>
 *   <li>Fuse both result sets via normalized score</li>
 *   <li>Return top-k {@link SearchResult}</li>
 * </ol>
 */
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public final class HybridRetrieverService implements RetrieverService {

    private static final String F_VECTOR = "vector";
    private static final String F_DOC_ID = "docId";
    private static final String F_CHUNK_ID = "chunkId";
    private static final String F_TEXT = "text";
    private static final String F_FROM_PAGE = "fromPage";
    private static final String F_TO_PAGE = "toPage";

    private final EmbeddingService embeddingService;
    private final SearcherManager searcherManager;

    /**
     * Executes hybrid semantic + keyword search.
     *
     * @param query user query string
     * @param k     maximum number of results
     * @return fused top-k results
     * @throws IOException if Lucene search fails
     */
    public List<SearchResult> search(final String query, final int k) throws IOException {
        final IndexSearcher searcher = searcherManager.acquire();
        try (final StandardAnalyzer standardAnalyzer = new StandardAnalyzer()) {
            // 1. Vector search
            final float[] vector = embeddingService.embedQuery(query);
            final KnnFloatVectorQuery knn = new KnnFloatVectorQuery(F_VECTOR, vector, k * 2); // oversample
            final TopDocs vectorDocs = searcher.search(knn, k * 2);

            // 2. BM25 keyword search
            final QueryParser parser = new QueryParser(F_TEXT, standardAnalyzer);
            final Query bm25Query = parser.parse(QueryParserBase.escape(query));
            final TopDocs keywordDocs = searcher.search(bm25Query, k * 2);

            // 3. Normalize scores
            final Map<Integer, Float> scoreMap = new HashMap<>();
            normalizeAndAccumulate(vectorDocs, scoreMap, 0.6f); // weight vectors 60%
            normalizeAndAccumulate(keywordDocs, scoreMap, 0.4f); // weight keywords 40%

            // 4. Sort fused docs by combined score
            return scoreMap.entrySet().stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .limit(k)
                    .map(e -> toSearchResult(searcher, e.getKey(), e.getValue()))
                    .toList();

        } catch (final Exception e) {
            log.error("Hybrid retrieval failed for query: {}", query, e);
            return Collections.emptyList();
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * Normalize scores within TopDocs and accumulate weighted into scoreMap.
     */
    private void normalizeAndAccumulate(final TopDocs docs,
                                        final Map<Integer, Float> scoreMap,
                                        final float weight) {
        if (docs.scoreDocs.length == 0) {
            return;
        }
        float maxScore = Arrays.stream(docs.scoreDocs)
                .map(sd -> sd.score)
                .max(Float::compare)
                .orElse(1.0f);

        for (final ScoreDoc sd : docs.scoreDocs) {
            final float normalized = sd.score / maxScore;
            scoreMap.merge(sd.doc, normalized * weight, Float::sum);
        }
    }

    /**
     * Convert Lucene docId + score to SearchResult DTO.
     */
    private SearchResult toSearchResult(final IndexSearcher searcher,
                                        final int luceneDocId,
                                        final float score) {
        try {
            final Document doc = searcher.doc(luceneDocId);

            final int fromPage = doc.getField(F_FROM_PAGE) == null ? 0 : doc.getField(F_FROM_PAGE).numericValue().intValue();
            final int toPage = doc.getField(F_TO_PAGE) == null ? 0 : doc.getField(F_TO_PAGE).numericValue().intValue();

            return new SearchResult(
                    doc.get(F_DOC_ID),
                    doc.get(F_CHUNK_ID),
                    doc.get(F_TEXT),
                    fromPage,
                    toPage,
                    score);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load document id=" + luceneDocId, e);
        }
    }
}
