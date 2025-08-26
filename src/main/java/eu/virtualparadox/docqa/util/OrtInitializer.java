package eu.virtualparadox.docqa.util;

import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrtInitializer {

    private OrtInitializer() {
        // Prevent instantiation
    }

    /**
     * Initializes OrtSession.SessionOptions with the best available execution provider
     * based on the current operating system.
     */
    public static OrtSession.SessionOptions initializeOrt() {
        try {
            final OrtSession.SessionOptions opts = new OrtSession.SessionOptions();

            // leave one core free for other tasks
            int availableProcessors = Runtime.getRuntime().availableProcessors() - 1;
            int intraThreads = Math.max(1, availableProcessors);

            opts.setIntraOpNumThreads(intraThreads);
            opts.setInterOpNumThreads(1);

            log.info("Intra-op threads: {}, Inter-op threads: {}", intraThreads, 1);
            return opts;
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ONNX Runtime", e);
        }
    }
}


