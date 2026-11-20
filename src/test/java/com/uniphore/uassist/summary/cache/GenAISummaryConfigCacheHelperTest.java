package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenAISummaryConfigCacheHelperTest {

  @Mock
  private CacheOperations<GenAISummaryConfig> genAISummaryConfigCache;

  @Mock
  private HashLocalCache<GenAISummaryConfig> genAISummaryConfigLocalCache;

  @Mock
  private ConfigChangeEventPublisher configChangeEventHandler;

  @InjectMocks
  private GenAISummaryConfigCacheHelper cacheHelper;

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testSet() {
    GenAISummaryConfig config = getGenAISummaryConfig();
    cacheHelper.set(config);
    verify(genAISummaryConfigCache).put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG),
        config.getExperienceId(), config);
    verify(configChangeEventHandler).publishDeleteMessage("", Constants.GEN_AI_SUMMARY, "",
        config.getTenantId(), config.getEnvironment(), config.getExperienceId());
  }

  private static GenAISummaryConfig getGenAISummaryConfig() {
    GenAISummaryConfig config =
        GenAISummaryConfig.builder().tenantId("tenant1").environment("env1").experienceId("exp1")
            .config("{}").build();
    return config;
  }

  @Test
  void testSetForDeployment() {
    GenAISummaryConfig config = getGenAISummaryConfig();
    cacheHelper.setForDeployment(config, "env2");
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, config.getTenantId());
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "env2");
    verify(genAISummaryConfigCache).put(CommonCacheHelper.getCommonCacheKeyforDeployment(
            CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG, tenantMap), config.getExperienceId(),
        config);
  }

  @Test
  void testFind() {
    GenAISummaryConfig config = getGenAISummaryConfig();
    when(genAISummaryConfigLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(
        genAISummaryConfigCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        config);
    GenAISummaryConfig result = cacheHelper.find("exp1");
    assertNotNull(result);
    assertEquals(config, result);
    verify(genAISummaryConfigLocalCache).put(anyString(), anyString(), any());
  }

  @Test
  void testGetGenAIConfigCacheByExperience() throws Exception {
    GenAISummaryConfig config = GenAISummaryConfig.builder()
        .config(objectMapper.writeValueAsString(GenAISummaryCache.builder().build())).build();
    when(genAISummaryConfigLocalCache.get(anyString(), anyString())).thenReturn(config);
    Optional<GenAISummaryCache> result = cacheHelper.getGenAIConfigCacheByExperience("exp1");
    assertTrue(result.isPresent());
    assertNotNull(result.get());
  }

  @Test
  void testGetGenAIConfigCacheByExperience_IfNotFoundInCache() {
    when(genAISummaryConfigLocalCache.get(anyString(), anyString())).thenReturn(null);
    Optional<GenAISummaryCache> result = cacheHelper.getGenAIConfigCacheByExperience("exp1");
    assertTrue(true);
  }

  @Test
  void testGetGenAIConfigCacheByExperience_IfThrowsException() {
    when(genAISummaryConfigLocalCache.get(anyString(), anyString())).thenThrow(
        new RuntimeException());
    Optional<GenAISummaryCache> result = cacheHelper.getGenAIConfigCacheByExperience("exp1");
    assertEquals(Optional.empty(), result);
  }

  @Test
  void testRemove() {
    cacheHelper.remove("dev");
    verify(genAISummaryConfigCache).delete(CommonCacheHelper.getCommonCacheKeyforDeployment(
        CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG, TenantContext.getMap()));
  }
}
