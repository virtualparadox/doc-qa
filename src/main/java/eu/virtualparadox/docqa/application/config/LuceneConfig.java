package eu.virtualparadox.docqa.application.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates and manages Lucene resources (Directory, Analyzer, IndexWriter, SearcherManager).
 * <p>Resources are opened against the on-disk index under {@code docqa.index} and closed on shutdown.</p>
 */
@Configuration
@Slf4j
public class LuceneConfig {

    private Directory directory;
    private IndexWriter indexWriter;
    private SearcherManager searcherManager;
    private Analyzer analyzer;

    /**
     * Provides the Lucene FS directory bound to the configured index path.
     *
     * @param props system properties (resolved from application.properties)
     * @return opened {@link Directory}
     * @throws IOException if the path cannot be created or opened
     */
    @Bean
    public Directory luceneDirectory(final ApplicationConfig props) throws IOException {
        final Path indexPath = props.getIndex();
        Files.createDirectories(indexPath);
        this.directory = FSDirectory.open(indexPath);
        return this.directory;
    }

    /**
     * Provides a shared, general-purpose analyzer.
     *
     * @return {@link StandardAnalyzer} instance
     */
    @Bean
    public Analyzer analyzer() {
        this.analyzer = new StandardAnalyzer();
        return this.analyzer;
    }

    /**
     * Provides the Lucene IndexWriter configured for create-or-append mode.
     *
     * @param dir      Lucene directory
     * @param analyzer text analyzer
     * @return {@link IndexWriter}
     * @throws IOException on writer creation error
     */
    @Bean
    public IndexWriter indexWriter(final Directory dir, final Analyzer analyzer) throws IOException {
        final IndexWriterConfig cfg = new IndexWriterConfig(analyzer)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.indexWriter = new IndexWriter(dir, cfg);
        return this.indexWriter;
    }

    /**
     * Provides a {@link SearcherManager} for near-real-time search.
     *
     * @param writer index writer
     * @return {@link SearcherManager}
     * @throws IOException on failure
     */
    @Bean
    public SearcherManager searcherManager(final IndexWriter writer) throws IOException {
        this.searcherManager = new SearcherManager(writer, null);
        return this.searcherManager;
    }

    /**
     * Ensures Lucene resources are closed cleanly on shutdown.
     */
    @PreDestroy
    public void close() {
        try { if (searcherManager != null) searcherManager.close(); } catch (Exception e) {
            log.error("Unable to close SearcherManager", e);
        }

        try { if (indexWriter != null) indexWriter.close(); } catch (Exception e) {
            log.error("Unable to close IndexWriter", e);
        }

        try { if (analyzer != null) analyzer.close(); } catch (Exception e) {
            log.error("Unable to close Analyzer", e);
        }

        try { if (directory != null) directory.close(); } catch (Exception e) {
            log.error("Unable to close Directory", e);
        }
    }
}
