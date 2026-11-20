package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.DispositionDao;
import com.uniphore.uassist.summary.model.CacheRefreshBean;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.service.HeaderTemplateService;
import com.uniphore.uassist.summary.service.IntentTemplateService;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class CacheRefreshHelperTest {

  @Mock
  private DispositionDao dispositionDao;

  @Mock
  private DispositionCacheHelper dispositionCache;

  @Mock
  private CacheOperations<Boolean> cacheOperations;

  @Mock
  private HeaderTemplateService headerTemplateService;

  @Mock
  private IntentTemplateService intentTemplateService;

  @Mock
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @Mock
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @InjectMocks
  private CacheRefreshHelper cacheRefreshHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  @Order(1)
  public void testRefreshHeaderTemplate_CacheNotExists() {
    CacheRefreshBean cacheRefreshBean = getCacheRefreshBean();
    HeaderTemplate headerTemplate = HeaderTemplate.builder().template("testTemplate").build();
    when(headerTemplateService.fetchHeaderTemplate(eq("businessProcessId"))).thenReturn(
        headerTemplate);
    when(cacheOperations.get(any(String.class), any(String.class),
        any(TypeReference.class))).thenReturn(null);
    String result = cacheRefreshHelper.refreshHeaderTemplate(cacheRefreshBean);
    assertEquals("testTemplate", result);
  }

  @Test
  @Order(2)
  public void testRefreshHeaderTemplate_CacheExists() {
    CacheRefreshBean cacheRefreshBean = getCacheRefreshBean();
    when(cacheOperations.get(any(String.class), any(String.class),
        any(TypeReference.class))).thenReturn(Boolean.TRUE);
    String result = cacheRefreshHelper.refreshHeaderTemplate(cacheRefreshBean);
    assertEquals(StringUtils.EMPTY, result);
  }

  private static CacheRefreshBean getCacheRefreshBean() {
    CacheRefreshBean cacheRefreshBean =
        CacheRefreshBean.builder().businessProcessId("businessProcessId").build();
    return cacheRefreshBean;
  }

  @Test
  @Order(3)
  public void testRefreshIntentTemplate_CacheNotExists() {
    CacheRefreshBean cacheRefreshBean = getRefreshBean();
    IntentTemplate intentTemplate = IntentTemplate.builder().template("intentTemplate").build();
    when(intentTemplateService.getIntentTemplate(eq("intentId"))).thenReturn(intentTemplate);
    when(cacheOperations.get(any(String.class), any(String.class),
        any(TypeReference.class))).thenReturn(null);
    String result = cacheRefreshHelper.refreshIntentTemplate(cacheRefreshBean);
    assertEquals("intentTemplate", result);
  }

  @Test
  @Order(4)
  public void testRefreshIntentTemplate_CacheExists() {
    CacheRefreshBean cacheRefreshBean = getRefreshBean();
    when(cacheOperations.get(any(String.class), any(String.class),
        any(TypeReference.class))).thenReturn(Boolean.TRUE);
    String result = cacheRefreshHelper.refreshIntentTemplate(cacheRefreshBean);
    assertEquals(StringUtils.EMPTY, result);
  }

  private static CacheRefreshBean getRefreshBean() {
    CacheRefreshBean cacheRefreshBean = CacheRefreshBean.builder().intentId("intentId").build();
    return cacheRefreshBean;
  }

  @Test
  @Order(5)
  public void testCheckIfAlreadyRequestSent_LockAcquired() {
    getCacheRefreshBean();
    when(cacheOperations.get(any(String.class), any(String.class),
        any(TypeReference.class))).thenReturn(null);
    boolean result =
        cacheRefreshHelper.checkIfAlreadyRequestSent("HEADER_KEY", "businessProcessId");
    assertFalse(result);
  }
}

