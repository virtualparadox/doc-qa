package eu.virtualparadox.docqa.rag.rerank.model;

/**
 * @param docId    Identifier of the parent document.
 * @param chunkId  Identifier of the chunk inside the document.
 * @param text     The chunk text (retrieved from Lucene).
 * @param fromPage Starting page number of the chunk in the original document.
 * @param score    Rerank score
 */
public record RerankResult(String docId, String chunkId, String text, int fromPage, int toPage, float score) {

}
