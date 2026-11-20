package com.uniphore.uassist.summary.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;

import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GenAISummaryConfigCacheHelper {

  @Autowired
  private CacheOperations<GenAISummaryConfig> genAISummaryConfigCache;

  @Autowired
  @Qualifier("genAISummaryConfigLocalCache")
  private HashLocalCache<GenAISummaryConfig> genAISummaryConfigLocalCache;

  @Autowired
  private ConfigChangeEventPublisher configChangeEventHandler;

  private final com.fasterxml.jackson.databind.ObjectMapper ObjectMapper = new ObjectMapper();

  public void set(final GenAISummaryConfig genAISummaryConfig) {
    log.debug(
        "Save the GenAISummaryConfig to cache tenantId: {}, environment : {}, experienceId: {} ",
        genAISummaryConfig.getTenantId(), genAISummaryConfig.getEnvironment(),
        genAISummaryConfig.getExperienceId());
    this.genAISummaryConfigCache.put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG),
        genAISummaryConfig.getExperienceId(), genAISummaryConfig);
    
    this.configChangeEventHandler.publishDeleteMessage(StringUtil.EMPTY_STRING, Constants.GEN_AI_SUMMARY,
        StringUtil.EMPTY_STRING, genAISummaryConfig.getTenantId(),
        genAISummaryConfig.getEnvironment(), genAISummaryConfig.getExperienceId());
  }

  public void setForDeployment(final GenAISummaryConfig genAISummaryConfig, String environment) {
    log.debug(
        "Save the GenAISummaryConfig to cache tenantId: {}, environment : {}, experienceId: {} ",
        genAISummaryConfig.getTenantId(), genAISummaryConfig.getEnvironment(),
        genAISummaryConfig.getExperienceId());
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, genAISummaryConfig.getTenantId());
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, environment);
    this.genAISummaryConfigCache.put(
        CommonCacheHelper.getCommonCacheKeyforDeployment(
            CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG, tenantMap),
        genAISummaryConfig.getExperienceId(), genAISummaryConfig);
  }

  public GenAISummaryConfig find(final String experienceId) {
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG);
    GenAISummaryConfig genAISummaryConfig =
        this.genAISummaryConfigLocalCache.get(key, experienceId);

    if (genAISummaryConfig == null) {
      genAISummaryConfig = this.genAISummaryConfigCache.get(key, experienceId,
          new TypeReference<GenAISummaryConfig>() {});
      this.genAISummaryConfigLocalCache.put(key, experienceId, genAISummaryConfig);
    }
    return genAISummaryConfig;
  }

  public Optional<GenAISummaryCache> getGenAIConfigCacheByExperience(final String expId) {
    try {
      GenAISummaryConfig genAISummaryConfig = this.find(expId);
      if (genAISummaryConfig != null) {
        log.debug("genAi summary config:{}",genAISummaryConfig);
        Optional<GenAISummaryCache> genAISummaryCache=Optional.ofNullable(this.ObjectMapper.readValue(genAISummaryConfig.getConfig(),
            new TypeReference<GenAISummaryCache>() {}));
        log.debug("genAi summary cache:{}",genAISummaryCache);
        return genAISummaryCache;
      } else {
        log.debug("GenAI config not found for experience id: {}", expId);
      }

    } catch (Exception e) {
      log.error(
          "error at fetching and converting genAiconfig from cache experienceId: {}, error: {}",
          expId, e);
    }
    return Optional.empty();
  }

  public void remove(final String environment) {
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID,
        TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID));
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, environment);

    log.info(
        "remove the GenAISummaryConfig from cache tenantId: {} , environment :{} for deployment ",
        tenantMap.get(TenantConstant.META_KEY_TENANT_ID),
        tenantMap.get(TenantConstant.META_KEY_ENVIRONMENT));
    this.genAISummaryConfigCache.delete(CommonCacheHelper.getCommonCacheKeyforDeployment(
        CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG, tenantMap));
  }
}
