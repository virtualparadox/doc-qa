package eu.virtualparadox.docqa.query;

import eu.virtualparadox.docqa.application.executor.QuestionExecutor;
import eu.virtualparadox.docqa.query.citation.Citation;
import eu.virtualparadox.docqa.query.citation.CitationResolverService;
import eu.virtualparadox.docqa.rag.answer.AnswerService;
import eu.virtualparadox.docqa.rag.rerank.model.RerankResult;
import eu.virtualparadox.docqa.rag.rerank.service.RerankService;
import eu.virtualparadox.docqa.rag.retriever.model.SearchResult;
import eu.virtualparadox.docqa.rag.retriever.service.RetrieverService;
import eu.virtualparadox.docqa.query.question.QuestionJob;
import eu.virtualparadox.docqa.query.question.QuestionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static eu.virtualparadox.docqa.query.question.EQuestionStatus.*;

@Service
@Slf4j
@RequiredArgsConstructor
public final class QueryManager {

    private final RetrieverService retrieverService;
    private final RerankService rerankService;
    private final AnswerService answerService;
    private final QuestionRegistry registry;
    private final QuestionExecutor questionExecutor;
    private final CitationResolverService citationResolverService;

    public QuestionJob submitQuery(final String query) {
        final QuestionJob job = registry.createJob(query);
        questionExecutor.submit(() -> process(job));
        return job;
    }

    private void process(final QuestionJob job) {
        try {
            registry.updateStatus(job.getId(), RETRIEVING, null);
            final List<SearchResult> raw = retrieverService.search(job.getQuery(), 100);
            printDebugRetrieved(raw);

            registry.updateStatus(job.getId(), RERANKING, null);
            final List<RerankResult> ranked = rerankService.rerank(job.getQuery(), raw);
            final List<RerankResult> limited = limit(ranked, 50);
            printDebugLimited(limited);

            if (limited.isEmpty()) {
                registry.updateStatus(job.getId(), COMPLETED, "No relevant information found.");
                return;
            }

            final List<Citation> citations = citationResolverService.getCitations(limited);
            printDebugCitations(citations);

            registry.updateStatus(job.getId(), ANSWERING, null);
            final String answer = answerService.buildHTMLAnswer(job.getQuery(), limited, citations);
            log.info(" !!! Distilled answer:\n{}", answer);

            registry.updateStatus(job.getId(), COMPLETED, answer);
        } catch (Exception ex) {
            log.error("Job {} failed", job.getId(), ex);
            registry.updateStatus(job.getId(), FAILED, ex.getMessage());
        }
    }

    private void printDebugCitations(List<Citation> citations) {
        final StringBuilder sb = new StringBuilder();
        for (final Citation c : citations) {
            sb.append(" - ").append(c.asString()).append("\n");
        }
        log.debug(" !!! Citations for answer:\n{}", sb);
    }

    private void printDebugLimited(List<RerankResult> limited) {
        final StringBuilder sb = new StringBuilder();
        for (final RerankResult r : limited) {
            sb.append(" - ").append("[").append(r.score()).append("] ").append(r.text()).append("\n");
        }
        log.debug(" !!! Limited to top chunks:\n{}", sb);
    }

    private void printDebugRetrieved(List<SearchResult> raw) {
        final StringBuilder sb = new StringBuilder();
        for (final SearchResult r : raw) {
            sb.append(" - ").append("[").append(r.score()).append("] ").append(r.text()).append("\n");
        }
        // switched to debug to avoid spamming logs with large payloads
        log.debug(" !!! Retrieved chunks:\n{}", sb);
    }

    private List<RerankResult> limit(List<RerankResult> ranked, int topK) {
        // avoid subList + removeIf on a view; use a non-mutating pipeline
        return ranked.stream()
                .filter(r -> r.score() >= 0)
                .limit(topK)
                .toList();
    }

    public Optional<QuestionJob> getJob(Long currentJobId) {
        return registry.getJob(currentJobId);
    }
}
