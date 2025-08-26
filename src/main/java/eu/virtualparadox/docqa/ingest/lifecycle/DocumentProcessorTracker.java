package eu.virtualparadox.docqa.ingest.lifecycle;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;

/**
 * Tracks the progress of document processing across multiple documents and chunks.
 *
 * Provides both global (all documents) and per-document progress percentages.
 */
@Service
@Slf4j
public final class DocumentProcessorTracker {

    /**
     * Queue of remaining documents to process, represented by their chunk counts.
     */
    private final Queue<Integer> documentQueue = new LinkedList<>();

    /**
     * Total number of chunks across all documents.
     */
    private int totalChunks = 0;

    /**
     * Total number of chunks processed so far (across all documents).
     */
    private int processedChunks = 0;

    /**
     * Current document total chunk count.
     */
    private int currentDocumentChunks = 0;

    /**
     * Current document processed chunks.
     */
    private int currentDocumentProcessed = 0;

    /**
     * Optional callback: gets invoked after each step with (totalPercent, documentPercent).
     * -- SETTER --
     *  Sets a callback that receives (totalPercent, documentPercent).
     *
     * @param callback progress callback

     */
    @Setter
    private BiConsumer<Integer, Integer> progressCallback;

    /**
     * Adds a new document to the processing queue.
     *
     * @param chunkCount the number of chunks in the document
     */
    public void addDocument(final int chunkCount) {
        documentQueue.add(chunkCount);
        totalChunks += chunkCount;

        // if no current doc set, assign this one
        if (currentDocumentChunks == 0) {
            startNextDocument();
        }

        log.info("Added document with {} chunks. Total chunks to process: {}", chunkCount, totalChunks);
    }

    /**
     * Called when a chunk is processed by the embedder.
     * Updates counters and fires callback.
     */
    public void step() {
        if (currentDocumentChunks == 0) {
            return; // nothing to process
        }

        processedChunks++;
        currentDocumentProcessed++;

        if (currentDocumentProcessed >= currentDocumentChunks) {
            // finished current doc, move to next
            startNextDocument();
        }

        fireProgressEvent();
    }

    /**
     * Moves to the next document in the queue, if any.
     */
    private void startNextDocument() {
        currentDocumentProcessed = 0;
        currentDocumentChunks = documentQueue.isEmpty() ? 0 : documentQueue.poll();
    }

    /**
     * Notifies the callback with progress percentages.
     */
    private void fireProgressEvent() {
        if (progressCallback == null) {
            return;
        }

        final ProgressStatus progressStatus = getProgressStatus();
        progressCallback.accept(progressStatus.totalPercent(), progressStatus.documentPercent());
    }

    public ProgressStatus getProgressStatus() {
        final int totalPercent =
                totalChunks == 0 ? 0 : (int) ((processedChunks * 100L) / totalChunks);

        final int documentPercent =
                currentDocumentChunks == 0 ? 100 : (int) ((currentDocumentProcessed * 100L) / currentDocumentChunks);

        return new ProgressStatus(totalPercent, documentPercent);
    }
}
