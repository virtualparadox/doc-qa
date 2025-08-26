package eu.virtualparadox.docqa.application.config;

import eu.virtualparadox.docqa.application.executor.IngestionExecutor;
import eu.virtualparadox.docqa.application.executor.QuestionExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    @Bean
    public IngestionExecutor ingestionExecutor() {
        IngestionExecutor executor = new IngestionExecutor();
        executor.setCorePoolSize(1);        // only one worker thread
        executor.setMaxPoolSize(1);         // prevent scaling up
        executor.setQueueCapacity(Integer.MAX_VALUE); // unlimited queue
        executor.setThreadNamePrefix("ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean
    public QuestionExecutor questionExecutor() {
        QuestionExecutor executor = new QuestionExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("question-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}