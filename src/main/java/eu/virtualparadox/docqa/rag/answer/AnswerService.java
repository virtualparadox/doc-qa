package eu.virtualparadox.docqa.rag.answer;

import eu.virtualparadox.docqa.query.citation.Citation;
import eu.virtualparadox.docqa.rag.rerank.model.RerankResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AnswerService that distills reranked evidence into a safer pack.
 * Ensures causal events and related entities are preserved, while keeping
 * the output concise and faithful to the source text.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnswerService {

    private final ChatModel chatModel;

    /**
     * Builds an evidence pack:
     * - Retains direct and indirect connections
     * - Preserves causal/transformative events (how/why answers)
     * - Allows broad coverage without runaway verbosity
     *
     * @param query     the user query
     * @param docs      reranked candidate passages
     * @param citations
     * @return distilled evidence pack (bullet-pointed plain text)
     */
    public String buildHTMLAnswer(final String query,
                                  final List<RerankResult> docs,
                                  final List<Citation> citations) {
        // ==== 1. INSTRUCTIONS (system/developer role) ====
        String instructions = String.join("\n",
                "You are a question answering system. Follow these rules:",
                "0. Do not invent or fabricate facts; use ONLY the CONTEXT provided.",
                "1. Extract sentences directly OR indirectly relevant to the question.",
                "2. Always include causal or transformative events when the question asks HOW or WHY.",
                "3. Preserve important related entities, concepts, and events even if not explicitly named in the query.",
                "4. Organize the output into bullet points grouped by related concepts, topics, or entities where appropriate.",
                "5. Keep wording faithful to the source text (light trimming is OK, no paraphrasing).",
                "6. If unsure whether a fact is relevant, INCLUDE it rather than omit it.",
                "7. Output as formatted HTML with bullet points.",
                "8. If multiple important facts exist for the same concept or entity, include up to THREE bullets.",
                "9. Keep the entire output concise: maximum ~100 lines total.",
                "10. Start your answer with a short summary paragraph."
        );

        // ==== 2. CONTEXT + QUESTION (user role) ====
        final StringBuilder contextBuilder = new StringBuilder("CONTEXT:\n");
        for (RerankResult doc : docs) {
            contextBuilder.append("- ").append(doc.text()).append("\n");
        }
        contextBuilder.append("\nQUESTION: ").append(query);

        // ==== 3. Prompt ====
        final Prompt prompt = new Prompt(
                new SystemMessage(instructions),
                new UserMessage(contextBuilder.toString())
        );

        log.info(" !!! Prompt: \nSystem: {}\nUser: {}", instructions, contextBuilder);

        final String generatedAnswer = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();

        if (generatedAnswer == null || generatedAnswer.isBlank()) {
            return "<p><i>No answer could be generated.</i></p>";
        }

        // Append citations
        final StringBuilder answerWithCitations = new StringBuilder(generatedAnswer);
        answerWithCitations.append("<p>Sources:</p>");
        answerWithCitations.append("<ul>\n");
        for (Citation c : citations) {
            answerWithCitations.append("<li>").append(c.asString()).append("</li>\n");
        }
        answerWithCitations.append("</ul>\n");

        return answerWithCitations.toString();
    }
}
