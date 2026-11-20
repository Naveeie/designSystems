package com.uniphore.uassist.summary.cache;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonCacheHelperTest {

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testGetSummaryCacheKey() {
    String expectedKey = String.format("%s.%s", CommonCacheHelper.CACHE_PREFIX_SUMMARY,
        "3e37631c-78ac-4137-8415-72dcd90b19fd.dev");
    String actualKey = CommonCacheHelper.getSummaryCacheKey();
    assertEquals(expectedKey, actualKey);
  }

  @Test
  void testGetAgentStepsCacheKey() {
    String expectedKey = String.format("%s.%s", CommonCacheHelper.CACHE_PREFIX_AGENT_STEPS,
        "3e37631c-78ac-4137-8415-72dcd90b19fd.dev");
    String actualKey = CommonCacheHelper.getAgentStepsCacheKey();
    assertEquals(expectedKey, actualKey);
  }

  @Test
  void testGetGenAISummaryCacheKey() {
    String expectedKey =
        String.format("%s.%s", CommonCacheHelper.CACHE_PREFIX_GENAI_SUMMARY_REQUESTED,
            "3e37631c-78ac-4137-8415-72dcd90b19fd.dev");
    String actualKey = CommonCacheHelper.getGenAISummaryCacheKey();
    assertEquals(expectedKey, actualKey);
  }

  @Test
  void testGetCommonCacheKey() {
    String type = "intent-template";
    String expectedKey =
        String.format("%s.%s", "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev",
            type);
    String actualKey = CommonCacheHelper.getCommonCacheKey(type);
    assertEquals(expectedKey, actualKey);
  }

  @Test
  void testGetCommonCacheKeyforDeployment() {
    String type = "intent-template";
    String expectedKey =
        String.format("%s.%s", "uniphore.summary.config.3e37631c-78ac-4137-8415-72dcd90b19fd.dev",
            type);
    String actualKey =
        CommonCacheHelper.getCommonCacheKeyforDeployment(type, TenantContext.getMap());
    assertEquals(expectedKey, actualKey);
  }

  @Test
  void testGetUserCacheKey() {
    String type = "user";
    String expectedKey =
        String.format("%s.%s", "uniphore.summary.3e37631c-78ac-4137-8415-72dcd90b19fd.dev", type);
    String actualKey = CommonCacheHelper.getUserCacheKey(type);
    assertEquals(expectedKey, actualKey);
  }

  @Test
  void testGetRefreshCacheKey() {
    String type = "refresh";
    String expectedKey = String.format("%s.%s",
        "uniphore.summary.cache-refresh.3e37631c-78ac-4137-8415-72dcd90b19fd.dev", type);
    String actualKey = CommonCacheHelper.getRefreshCacheKey(type);
    assertEquals(expectedKey, actualKey);
  }
}

