package com.kisshore19.nseinsights.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadPoolConfig {

    @Value("${nse.download.thread-pool-size:5}")
    private int threadPoolSize;

    @Bean(name = "nseDownloadExecutor")
    public ExecutorService nseDownloadExecutor() {
        return Executors.newFixedThreadPool(threadPoolSize);
    }
}