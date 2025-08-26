package eu.virtualparadox.docqa.ingest.lifecycle;

/**
 * Progress status of an ingestion task.
 *
 * @param totalPercent    Overall progress percentage (0-100)
 * @param documentPercent Progress percentage for the current document (0-100)
 */
public record ProgressStatus(int totalPercent, int documentPercent) {

}
