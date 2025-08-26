package eu.virtualparadox.docqa.application.executor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class QuestionExecutor extends ThreadPoolTaskExecutor {
}
