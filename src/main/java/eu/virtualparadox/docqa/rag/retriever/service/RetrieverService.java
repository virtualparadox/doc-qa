package eu.virtualparadox.docqa.rag.retriever.service;

import eu.virtualparadox.docqa.rag.retriever.model.SearchResult;

import java.io.IOException;
import java.util.List;

public interface RetrieverService {

    List<SearchResult> search(final String query, final int k) throws IOException;

}
