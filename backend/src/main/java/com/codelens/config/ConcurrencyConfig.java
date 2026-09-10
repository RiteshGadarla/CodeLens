package com.codelens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ConcurrencyConfig {

    // cpu-bound parsing/resolution
    @Bean(destroyMethod = "shutdown")
    ExecutorService analysisExecutor(CodeLensProperties props) {
        int n = props.analysis().parallelism() > 0
                ? props.analysis().parallelism()
                : Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(n, Thread.ofPlatform().name("analysis-", 0).daemon().factory());
    }

    // analysis jobs: io + orchestration
    @Bean(destroyMethod = "shutdown")
    ExecutorService jobExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("job-", 0).factory());
    }
}
