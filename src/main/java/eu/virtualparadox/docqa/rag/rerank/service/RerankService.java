package eu.virtualparadox.docqa.rag.rerank.service;

import eu.virtualparadox.docqa.rag.rerank.model.RerankResult;
import eu.virtualparadox.docqa.rag.retriever.model.SearchResult;

import java.util.List;

/**
 * Service interface for re-ranking retrieved search results.
 * <p>
 * A re-ranker assigns a refined relevance score to each candidate result
 * based on the query and the document content. This is typically implemented
 * using a cross-encoder or other specialized model.
 */
public interface RerankService {

    /**
     * Rerank the given search results for the specified query.
     *
     * @param query   the user query string
     * @param results the initial search results from the retriever
     * @return a new list of results, re-ordered and scored according to relevance
     */
    List<RerankResult> rerank(String query, List<SearchResult> results);
}
