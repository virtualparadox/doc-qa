package eu.virtualparadox.docqa.ingest.extractor;

import eu.virtualparadox.docqa.ingest.model.Chunk;

import java.nio.file.Path;
import java.util.List;

public interface TextExtractor {

    List<Chunk> extractText(final String id, final Path path);

}
