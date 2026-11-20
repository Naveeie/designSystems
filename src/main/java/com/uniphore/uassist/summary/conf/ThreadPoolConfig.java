package com.uniphore.uassist.summary.conf;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

  @Value("${thread.core-pool-size}")
  private int threadPoolCorePoolSize;

  @Value("${thread.max-pool-size}")
  private int threadPoolMaxPoolSize;

  @Value("${thread.queue-capacity}")
  private int threadPoolQueueCapacity;

  @Value("${thread.keep-alive-time}")
  private int threadkeepAliveTime;

  @Value("${thread.core-pool-size}")
  private int corePoolSize;

  @Value("${thread.max-pool-size}")
  private int maxPoolSize;

  @Value("${thread.queue-capacity}")
  private int queueCapacity;

  @Value("${thread.keep-alive-time}")
  private int keepAliveTime;

  @Value("${app.graceful-shutdown.wait-time:30}")
  private int graceFullWaitPeriod;
  
  @Bean(name = "threadPoolMongoExecutor")
  public ThreadPoolTaskExecutor threadPoolMongoExecutor() {

    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(this.threadPoolCorePoolSize);
    executor.setMaxPoolSize(this.threadPoolMaxPoolSize);
    executor.setQueueCapacity(this.threadPoolQueueCapacity);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(this.graceFullWaitPeriod);
    return executor;
  }

  @Bean(name = "threadPoolExecutor")
  public ThreadPoolTaskExecutor threadPoolExecutor() {

    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(this.threadPoolCorePoolSize);
    executor.setMaxPoolSize(this.threadPoolMaxPoolSize);
    executor.setQueueCapacity(this.threadPoolQueueCapacity);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(this.graceFullWaitPeriod);
    return executor;
  }

  @Bean(name = "fixedThreadPoolExecutor")
  public ExecutorService fixedThreadPoolExecutor() {
    return new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, (long) this.keepAliveTime,
        TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(this.queueCapacity));
  }

  @Bean(name = "tenantOperationExecutorService")
  public ExecutorService tenantOperationExecutorService() {
    return new ThreadPoolExecutor(5, 10, 60, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(100));
  }
}
