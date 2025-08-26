package eu.virtualparadox.docqa.ingest.model;

/**
 * Immutable representation of a text chunk produced by parsing + chunking.
 * <p>Contains the parent document id, chunk id, and the text to be embedded.</p>
 */
public record Chunk(String docId, String chunkId, String text, int pageStart, int pageEnd) {

    public static Chunk ofSingleText(final String text) {
        return new Chunk("", "", text, 0, 0);
    }
}
