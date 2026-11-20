package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DispositionCacheHelperTest {

  @Mock
  private CacheOperations<Disposition> dispositionCache;

  @Mock
  private HashLocalCache<Disposition> dispositionLocalCache;

  @Mock
  private ConfigChangeEventPublisher configChangeEventHandler;

  @InjectMocks
  private DispositionCacheHelper dispositionCacheHelper;

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
    Disposition disposition = Disposition.builder().tenantId("tenant-id").environment("environment")
        .businessProcessId("businessProcessId").businessProcessId("bp-id").dispositionId("disp-id")
        .build();
    dispositionCacheHelper.set(disposition);
    verify(dispositionCache).put(
        eq(CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION)),
        eq(disposition.getBusinessProcessId()), eq(disposition));
    verify(configChangeEventHandler).publishDeleteMessage(eq(disposition.getDispositionId()),
        eq(Constants.DISPOSITION), eq(disposition.getBusinessProcessId()),
        eq(disposition.getTenantId()), eq(disposition.getEnvironment()),
        eq(StringUtil.EMPTY_STRING));
  }

  @Test
  void testFind_CacheMiss() {
    String businessProcessId = "bp-id";
    Disposition disposition = Disposition.builder().businessProcessId("bp-id").build();
    when(dispositionLocalCache.get(any(), eq(businessProcessId))).thenReturn(null);
    when(dispositionCache.get(any(), eq(businessProcessId), any(TypeReference.class))).thenReturn(
        disposition);
    Disposition result = dispositionCacheHelper.find(businessProcessId);
    assertEquals(disposition, result);
    verify(dispositionCache).get(
        eq(CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION)),
        eq(businessProcessId), any(TypeReference.class));
    verify(dispositionLocalCache).put(
        eq(CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION)),
        eq(businessProcessId), eq(disposition));
  }

  @Test
  void testFind_CacheHit() {
    String businessProcessId = "bp-id";
    Disposition disposition = Disposition.builder().businessProcessId(businessProcessId).build();
    when(dispositionLocalCache.get(any(), eq(businessProcessId))).thenReturn(disposition);
    Disposition result = dispositionCacheHelper.find(businessProcessId);
    assertEquals(disposition, result);
    verify(dispositionCache, never()).get(any(), any(), any());
  }

  @Test
  void testRemove() {
    String businessProcessId = "bp-id";
    dispositionCacheHelper.remove(businessProcessId);
    verify(dispositionCache).delete(
        eq(CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION)),
        eq(businessProcessId));
    verify(configChangeEventHandler).publishDeleteMessage(eq(businessProcessId),
        eq(Constants.DISPOSITION), eq(businessProcessId),
        eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev"), eq(StringUtil.EMPTY_STRING));
  }
}

