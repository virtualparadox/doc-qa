package eu.virtualparadox.docqa.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import eu.virtualparadox.docqa.query.QueryManager;
import eu.virtualparadox.docqa.query.question.QuestionJob;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static eu.virtualparadox.docqa.query.question.EQuestionStatus.COMPLETED;

@Route("ask")
@UIScope
@SpringComponent
@RequiredArgsConstructor
public final class AskView extends VerticalLayout {

    private final QueryManager queryManager;

    private final TextField queryField = new TextField();
    private final Button searchBtn = new Button("Search");

    private final Div answerCard = new Div();
    private final Div answerContent = new Div();

    private static final String[] DEMO_QUESTIONS = {
            "Who is Harkon Lukas and how he became a Darklord?",
            "Describe the geography and key locations within Barovia.",
            "What is the Old Svalich Road, why is it important, and where does it lead?",
            "Who was Tatyana and how did her relationship with Strahd von Zarovich shape the curse of Barovia?",
            "Explain the alliance and rivalry between Strahd and Azalin. What events defined their relationship?",
            "What role does the Hiregaard family play in the politics of Nova Vaasa?",
            "How do monstrous lycanthropes differ from natural lycanthropes in Ravenloft?",
            "How do curses and inherited traits manifest in the Legacies of the Zarovan and Dilisnya families?"
    };

    private Long currentQuestionId;

    @PostConstruct
    private void initView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        final VerticalLayout container = new VerticalLayout();
        container.setWidthFull();
        container.setSpacing(true);
        container.setPadding(false);
        container.addClassName("askview-container");

        final Div demoContainer = buildDemoQuestions();
        final HorizontalLayout searchRow = buildSearchRow();
        final H4 askQuestionHeader = new H4("Ask a question about your documents:");
        final H4 answerHeader = new H4("Answer:");
        configureAnswerCard();

        // Build tree once, attach in one go
        container.add(demoContainer, askQuestionHeader, searchRow, answerHeader, answerCard);
        add(container);

        // Polling for async status updates
        UI.getCurrent().setPollInterval(1000);
        UI.getCurrent().addPollListener(e -> checkStatus());
    }

    private Div buildDemoQuestions() {
        final Div demoContainer = new Div();
        demoContainer.setWidthFull();
        demoContainer.add(new H4("Try one of these demo questions:"));
        demoContainer.addClassName("demo-container");

        for (final String demoQ : DEMO_QUESTIONS) {
            demoContainer.add(createDemoChip(demoQ));
        }
        return demoContainer;
    }

    private HorizontalLayout buildSearchRow() {
        queryField.setWidthFull();
        final HorizontalLayout searchRow = new HorizontalLayout(queryField, searchBtn);
        searchRow.setWidthFull();
        searchRow.setAlignItems(Alignment.END);

        searchBtn.addClickListener(e -> submitQuery());
        return searchRow;
    }

    private void configureAnswerCard() {
        answerCard.add(answerContent);
        answerCard.addClassName("answer-card");
        answerContent.setText("No answer yet – ask a question!");
    }

    private Button createDemoChip(final String text) {
        final Button chip = new Button(text, e -> {
            queryField.setValue(text);
            submitQuery();
        });
        chip.addClassName("demo-chip");
        return chip;
    }

    private void submitQuery() {
        final String q = queryField.getValue();
        if (q == null || q.isBlank()) {
            Notification.show("Please enter a question");
            return;
        }
        final QuestionJob job = queryManager.submitQuery(q);
        currentQuestionId = job.getId();
        answerContent.setText("Submitted query. Job ID = " + currentQuestionId);
    }

    private void checkStatus() {
        if (currentQuestionId == null) return;

        queryManager.getJob(currentQuestionId).ifPresent(job -> {
            final String finalAnswer = job.getAnswer();
            if (job.getStatus() == COMPLETED && finalAnswer != null) {
                answerContent.getElement().setProperty("innerHTML", finalAnswer);
            } else {
                answerContent.getElement().setProperty("innerHTML", "<b>Status:</b> " + job.getStatus());
            }
        });
    }
}
