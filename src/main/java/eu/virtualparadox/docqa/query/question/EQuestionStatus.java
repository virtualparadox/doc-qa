package eu.virtualparadox.docqa.query.question;

public enum EQuestionStatus {
    QUEUED,
    RETRIEVING,
    RERANKING,
    CONTEXTUALIZING,
    ANSWERING,
    COMPLETED,
    FAILED
}
