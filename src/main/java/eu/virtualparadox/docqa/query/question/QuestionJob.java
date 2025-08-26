package eu.virtualparadox.docqa.query.question;

import java.time.Instant;

public class QuestionJob {
    private final long id;
    private final String query;
    private volatile EQuestionStatus status;
    private volatile String answer;
    private final Instant createdAt;

    public QuestionJob(long id, String query) {
        this.id = id;
        this.query = query;
        this.status = EQuestionStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public long getId() { return id; }
    public String getQuery() { return query; }
    public EQuestionStatus getStatus() { return status; }
    public String getAnswer() { return answer; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(EQuestionStatus status) { this.status = status; }
    public void setAnswer(String answer) { this.answer = answer; }
}