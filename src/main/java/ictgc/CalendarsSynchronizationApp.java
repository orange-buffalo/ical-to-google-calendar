package ictgc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Main entry point of the application.
 */
@SpringBootApplication(scanBasePackages = "ictgc")
@EnableScheduling
@Slf4j
public class CalendarsSynchronizationApp {

    public static void main(String[] args) {
        SpringApplication.run(CalendarsSynchronizationApp.class, args);
    }

    /**
     * Task executor to be used by calendar synchronization service.
     */
    @Bean(name = "userFlowExecutor")
    @Autowired
    public TaskExecutor taskExecutor(ApplicationProperties config) {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        int poolSize = config.getUsers().size() * 2;
        threadPoolTaskExecutor.setCorePoolSize(poolSize);
        return threadPoolTaskExecutor;
    }

}
