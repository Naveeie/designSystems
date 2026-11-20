package com.uniphore.uassist.summary.cache;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;

@Configuration
public class LocalCacheBeans {

  @Value("${local.cache.ttl.runtime:300}")
  private int runtimeCachettl;
  
  @Value("${local.cache.ttl.config:1800}")
  private int configCachettl;

  @Bean(name = "genAIsummaryCacheLocalCache")
  public HashLocalCache<Boolean> genAIsummaryCacheLocalCache() {
    return new HashLocalCache<Boolean>(runtimeCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "dispositionLocalCache")
  public HashLocalCache<Disposition> dispositionLocalCache() {
    return new HashLocalCache<Disposition>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "headerTemplateLocalCache")
  public HashLocalCache<String> headerTemplateLocalCache() {
    return new HashLocalCache<String>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "headerEntityMapLocalCache")
  public HashLocalCache<Set<String>> headerEntityMapLocalCache() {
    return new HashLocalCache<Set<String>>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "headerVariableMapLocalCache")
  public HashLocalCache<Set<String>> headerVariableMapLocalCache() {
    return new HashLocalCache<Set<String>>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "intentTemplateLocalCache")
  public HashLocalCache<String> intentTemplateLocalCache() {
    return new HashLocalCache<String>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "intentEntityMapLocalCache")
  public HashLocalCache<Set<String>> intentEntityMapLocalCache() {
    return new HashLocalCache<Set<String>>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "intentVariableMapLocalCache")
  public HashLocalCache<Set<String>> intentVariableMapLocalCache() {
    return new HashLocalCache<Set<String>>(configCachettl, TimeUnit.SECONDS);
  }

  @Bean(name = "genAISummaryConfigLocalCache")
  public HashLocalCache<GenAISummaryConfig> genAISummaryConfigLocalCache() {
    return new HashLocalCache<GenAISummaryConfig>(configCachettl, TimeUnit.SECONDS);
  }

}
