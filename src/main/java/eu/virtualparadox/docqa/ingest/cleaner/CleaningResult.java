package eu.virtualparadox.docqa.ingest.cleaner;

/**
 * Result of text cleaning operation.
 */
public class CleaningResult {
    private final String cleanText;
    private final int[] pageMap;

    public CleaningResult(String cleanText, int[] pageMap) {
        this.cleanText = cleanText;
        this.pageMap = pageMap;

        // Verify invariant
        if (cleanText.length() != pageMap.length) {
            throw new IllegalStateException(
                    "Cleaning broke the invariant: text length " + cleanText.length() +
                            " != pageMap length " + pageMap.length
            );
        }
    }

    public String getCleanText() {
        return cleanText;
    }

    public int[] getPageMap() {
        return pageMap.clone();
    }
}
