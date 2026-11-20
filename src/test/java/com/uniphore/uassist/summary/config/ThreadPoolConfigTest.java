package com.uniphore.uassist.summary.config;

import com.uniphore.uassist.summary.ApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadPoolConfigTest extends ApplicationTest {

  @Autowired
  private ThreadPoolTaskExecutor threadPoolMongoExecutor;

  @Autowired
  private ThreadPoolTaskExecutor threadPoolExecutor;

  @Autowired
  private ExecutorService fixedThreadPoolExecutor;

  @Autowired
  private ExecutorService tenantOperationExecutorService;

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

  @Test
  public void testThreadPoolMongoExecutor() {
    assertEquals(corePoolSize, threadPoolMongoExecutor.getCorePoolSize());
    assertEquals(maxPoolSize, threadPoolMongoExecutor.getMaxPoolSize());
  }

  @Test
  public void testThreadPoolExecutor() {
    assertEquals(corePoolSize, threadPoolExecutor.getCorePoolSize());
    assertEquals(maxPoolSize, threadPoolExecutor.getMaxPoolSize());
  }

  @Test
  public void testFixedThreadPoolExecutor() {
    assertTrue(fixedThreadPoolExecutor instanceof ThreadPoolExecutor);
    ThreadPoolExecutor executor = (ThreadPoolExecutor) fixedThreadPoolExecutor;
    assertEquals(corePoolSize, executor.getCorePoolSize());
    assertEquals(maxPoolSize, executor.getMaximumPoolSize());
    assertEquals(queueCapacity, executor.getQueue().remainingCapacity());
  }

  @Test
  public void testTenantOperationExecutorService() {
    assertTrue(tenantOperationExecutorService instanceof ThreadPoolExecutor);
    ThreadPoolExecutor executor = (ThreadPoolExecutor) tenantOperationExecutorService;
    assertEquals(5, executor.getCorePoolSize());
    assertEquals(10, executor.getMaximumPoolSize());
    assertEquals(100, executor.getQueue().remainingCapacity());
  }
}
