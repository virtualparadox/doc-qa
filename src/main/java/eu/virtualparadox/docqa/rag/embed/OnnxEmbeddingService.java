package eu.virtualparadox.docqa.rag.embed;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import eu.virtualparadox.docqa.util.OrtInitializer;
import eu.virtualparadox.docqa.application.config.ApplicationConfig;
import eu.virtualparadox.docqa.ingest.model.Chunk;
import eu.virtualparadox.docqa.ingest.lifecycle.DocumentProcessorTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.onnxruntime.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public final class OnnxEmbeddingService implements EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(OnnxEmbeddingService.class);

    private static final int MAX_LEN = 1024;

    private final DocumentProcessorTracker documentProcessorTracker;

    private final Path modelPath;
    private final Path tokenizerPath;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    public OnnxEmbeddingService(final DocumentProcessorTracker documentProcessorTracker,
                                final ApplicationConfig config) {
        this.documentProcessorTracker = documentProcessorTracker;

        final Path retrieverModelRoot = config.getModels().resolve("retriever");
        this.modelPath = retrieverModelRoot.resolve("model.onnx");
        this.tokenizerPath = retrieverModelRoot.resolve("tokenizer.json");
    }

    @PostConstruct
    public void init() throws IOException, OrtException {
        this.env = OrtEnvironment.getEnvironment();
        final OrtSession.SessionOptions options = OrtInitializer.initializeOrt();

        this.session = env.createSession(modelPath.toString(), options);
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);

        logger.info("Loaded ONNX embedding model: {}", modelPath);
        logger.info("Model expects inputs: {}", session.getInputNames());
    }

    @PreDestroy
    public void cleanup() throws OrtException {
        if (session != null) {
            session.close();
        }
        if (env != null) {
            env.close();
        }
    }

    @Override
    public List<float[]> embed(final List<Chunk> chunks) {
        List<float[]> result = new ArrayList<>();

        for (Chunk chunk : chunks) {
            result.addAll(embedBatch(List.of(chunk)));
            documentProcessorTracker.step();
        }

        return result;
    }

    private List<float[]> embedBatch(final List<Chunk> chunks) {
        try {
            final List<Encoding> encodings = new ArrayList<>();
            int maxLen = 0;

            for (final Chunk c : chunks) {
                final Encoding e = tokenizer.encode(c.text());
                encodings.add(e);
                maxLen = Math.max(maxLen, e.getIds().length);
            }
            if (maxLen > MAX_LEN) {
                maxLen = MAX_LEN;
            }

            final int batchSize = encodings.size();
            final long[][] inputIdArr = new long[batchSize][maxLen];
            final long[][] attnMaskArr = new long[batchSize][maxLen];
            final long[][] tokenTypeArr = new long[batchSize][maxLen];

            for (int i = 0; i < batchSize; i++) {
                final long[] ids = encodings.get(i).getIds();
                final long[] mask = encodings.get(i).getAttentionMask();
                final int len = Math.min(ids.length, maxLen);

                System.arraycopy(ids, 0, inputIdArr[i], 0, len);
                System.arraycopy(mask, 0, attnMaskArr[i], 0, len);
            }

            try (final OnnxTensor inputIds = OnnxTensor.createTensor(env, inputIdArr);
                 final OnnxTensor attentionMask = OnnxTensor.createTensor(env, attnMaskArr);
                 final OnnxTensor tokenTypeTensor = OnnxTensor.createTensor(env, tokenTypeArr)) {

                final Map<String, OnnxTensor> inputs = new HashMap<>();
                if (session.getInputNames().contains("input_ids")) {
                    inputs.put("input_ids", inputIds);
                }
                if (session.getInputNames().contains("attention_mask")) {
                    inputs.put("attention_mask", attentionMask);
                }
                if (session.getInputNames().contains("token_type_ids")) {
                    inputs.put("token_type_ids", tokenTypeTensor);
                }

                final OrtSession.Result result = session.run(inputs);
                final float[][][] embeddings = (float[][][]) result.get(0).getValue();

                final List<float[]> out = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    final float[][] tokenVectors = embeddings[i];
                    final float[] vec = meanPool(tokenVectors, attnMaskArr[i]);
                    normalize(vec);
                    out.add(vec);
                }

                return out;
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to embed batch", e);
        }
    }

    @Override
    public float[] embedQuery(final String text) {
        final List<Chunk> singleton = List.of(Chunk.ofSingleText(text));
        return embed(singleton).getFirst();
    }

    private float[] meanPool(final float[][] tokenVectors, final long[] attentionMask) {
        final int hiddenDim = tokenVectors[0].length;
        final float[] pooled = new float[hiddenDim];

        int validCount = 0;
        for (int i = 0; i < tokenVectors.length; i++) {
            if (attentionMask[i] == 1) {
                final float[] tokenVec = tokenVectors[i];
                for (int j = 0; j < hiddenDim; j++) {
                    pooled[j] += tokenVec[j];
                }
                validCount++;
            }
        }

        if (validCount > 0) {
            for (int j = 0; j < hiddenDim; j++) {
                pooled[j] /= validCount;
            }
        }
        return pooled;
    }

    private void normalize(final float[] vec) {
        double norm = 0.0;
        for (final float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0.0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= (float) norm;
            }
        }
    }
}
