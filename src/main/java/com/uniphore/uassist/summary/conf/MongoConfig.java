package com.uniphore.uassist.summary.conf;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoTimeoutException;

@Configuration
@EnableMongoRepositories(basePackages = "com.uniphore.uassist.summary.dao")
@EnableRetry
public class MongoConfig extends AbstractMongoClientConfiguration {

  @Value("${spring.data.mongodb.uri}")
  private String uri;

  @Value("${spring.data.mongodb.database}")
  private String database;

  @Override
  protected String getDatabaseName() {
    return database;
  }

  @Override
  @Bean
  @Retryable(value = {MongoSocketOpenException.class, MongoTimeoutException.class, Exception.class},
      maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
  public MongoClient mongoClient() {
    MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(uri)).retryReads(true).retryWrites(true)
        .applyToSocketSettings(builder -> builder.connectTimeout(5000, TimeUnit.MILLISECONDS))
        .build();
    return MongoClients.create(settings);
  }
}
