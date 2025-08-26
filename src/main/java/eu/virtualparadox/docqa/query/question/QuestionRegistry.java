package eu.virtualparadox.docqa.query.question;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class QuestionRegistry {

    private final AtomicLong counter;
    private final Map<Long, QuestionJob> jobs;

    public QuestionRegistry() {
        this.counter = new AtomicLong(0);
        this.jobs = new ConcurrentHashMap<>();
    }

    public QuestionJob createJob(String query) {
        long id = counter.incrementAndGet();
        QuestionJob job = new QuestionJob(id, query);
        jobs.put(id, job);
        return job;
    }

    public Optional<QuestionJob> getJob(long id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public void updateStatus(long id, EQuestionStatus status, String answer) {
        jobs.computeIfPresent(id, (k, job) -> {
            job.setStatus(status);
            if (answer != null) job.setAnswer(answer);
            return job;
        });
    }
}
