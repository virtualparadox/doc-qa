package eu.virtualparadox.docqa.rag.rerank.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import eu.virtualparadox.docqa.application.config.ApplicationConfig;
import eu.virtualparadox.docqa.rag.rerank.model.RerankResult;
import eu.virtualparadox.docqa.util.OrtInitializer;
import eu.virtualparadox.docqa.rag.retriever.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.*;

/**
 * ONNX-based reranker with windowed scoring to prevent information loss.
 *
 * Why windowing?
 * ---------------
 * The reranker model has a maximum input length (MAX_LEN = 512 tokens).
 * When we encode [query + passage], any passage longer than this will be
 * truncated by default. That means content at the *end* of the chunk (e.g.,
 * mentions of Tatyana or Sergei) may never be "seen" by the model.
 *
 * This implementation detects long encodings and splits them into overlapping
 * windows. Each window is scored independently, and the *maximum* score is
 * assigned back to the original passage. This ensures that relevant signals
 * are not lost simply due to length truncation.
 */
@Service
@Slf4j
public final class OnnxRerankService implements RerankService {

    /** Maximum token length supported by the reranker model. */
    private static final int MAX_LEN = 512;

    /** Size of each sliding window for long passages. */
    private static final int WINDOW_SIZE = 480;

    /** Overlap between windows (to avoid cutting entities in half). */
    private static final int WINDOW_OVERLAP = 50;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public OnnxRerankService(final ApplicationConfig applicationConfig) throws IOException, OrtException {
        final Path rerankerModelRoot = applicationConfig.getModels().resolve("reranker");

        final Path modelPath = rerankerModelRoot.resolve("model.onnx");
        final Path tokenizerPath = rerankerModelRoot.resolve("tokenizer.json");

        this.env = OrtEnvironment.getEnvironment();
        final OrtSession.SessionOptions options = OrtInitializer.initializeOrt();

        this.session = env.createSession(modelPath.toString(), options);
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);

        log.info("Loaded ONNX reranker model from {}", modelPath);
    }

    @Override
    public List<RerankResult> rerank(final String query,
                                     final List<SearchResult> candidates) {
        final List<RerankResult> reranked = new ArrayList<>();

        for (final SearchResult candidate : candidates) {
            try {
                final Encoding encoding = tokenizer.encode(query, candidate.text());

                float score;
                if (encoding.getIds().length > MAX_LEN) {
                    score = rerankWithWindows(encoding);
                } else {
                    score = runRerank(encoding);
                }

                reranked.add(new RerankResult(candidate.docId(), candidate.chunkId(), candidate.text(), candidate.fromPage(), candidate.toPage(), score));
            } catch (Exception e) {
                throw new IllegalStateException("Failed reranking candidate", e);
            }
        }

        reranked.sort(Comparator.comparingDouble(RerankResult::score).reversed());
        return reranked;
    }

    /**
     * Handles long passages by splitting them into overlapping windows.
     *
     * @param encoding combined query+passage encoding
     * @return best relevance score among all windows
     */
    private float rerankWithWindows(final Encoding encoding) throws OrtException {
        final long[] ids = encoding.getIds();
        final long[] mask = encoding.getAttentionMask();

        float bestScore = Float.NEGATIVE_INFINITY;

        for (int start = 0; start < ids.length; start += (WINDOW_SIZE - WINDOW_OVERLAP)) {
            final int end = Math.min(start + WINDOW_SIZE, ids.length);

            final long[] winIds = Arrays.copyOfRange(ids, start, end);
            final long[] winMask = Arrays.copyOfRange(mask, start, end);

            // Pad up to MAX_LEN
            long[] paddedIds = Arrays.copyOf(winIds, MAX_LEN);
            long[] paddedMask = Arrays.copyOf(winMask, MAX_LEN);

            float score = runRerank(paddedIds, paddedMask);
            if (score > bestScore) {
                bestScore = score;
            }

            if (end == ids.length) {
                break; // reached end
            }
        }

        return bestScore;
    }

    /**
     * Runs the reranker on a normal-length encoding.
     */
    private float runRerank(final Encoding encoding) throws OrtException {
        // Truncate/pad to MAX_LEN
        long[] ids = Arrays.copyOfRange(encoding.getIds(), 0, Math.min(MAX_LEN, encoding.getIds().length));
        long[] mask = Arrays.copyOfRange(encoding.getAttentionMask(), 0, Math.min(MAX_LEN, encoding.getAttentionMask().length));

        if (ids.length < MAX_LEN) {
            ids = Arrays.copyOf(ids, MAX_LEN);
            mask = Arrays.copyOf(mask, MAX_LEN);
        }

        return runRerank(ids, mask);
    }

    /**
     * Low-level rerank: executes the ONNX model and extracts a relevance score.
     */
    private float runRerank(final long[] ids,
                            final long[] mask) throws OrtException {
        try (OnnxTensor inputIds = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), new long[]{1, MAX_LEN});
             OnnxTensor attentionMask = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), new long[]{1, MAX_LEN});

             OrtSession.Result result = session.run(Map.of(
                     "input_ids", inputIds,
                     "attention_mask", attentionMask
             ))) {

            Object value = result.get(0).getValue();

            switch (value) {
                case float[][] logits2d -> {
                    // Most classification heads: [batch, num_labels]
                    if (logits2d[0].length == 1) {
                        return logits2d[0][0]; // single regression score
                    } else {
                        return logits2d[0][1]; // assume [not relevant, relevant]
                    }
                    // Most classification heads: [batch, num_labels]
                }
                case float[] logits1d -> {
                    return logits1d[0];
                }
                default -> throw new IllegalStateException("Unexpected output shape: " + value.getClass());
            }
        }
    }
}
