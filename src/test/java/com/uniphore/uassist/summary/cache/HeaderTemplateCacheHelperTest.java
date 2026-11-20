package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HeaderTemplateCacheHelperTest {

  @Mock
  private CacheOperations<String> headerTemplateCache;

  @Mock
  private CacheOperations<Set<String>> headerEntityMapCache;

  @Mock
  private CacheOperations<Set<String>> headerVariableMapCache;

  @Mock
  @Qualifier("headerTemplateLocalCache")
  private HashLocalCache<String> headerTemplateLocalCache;

  @Mock
  @Qualifier("headerEntityMapLocalCache")
  private HashLocalCache<Set<String>> headerEntityMapLocalCache;

  @Mock
  @Qualifier("headerVariableMapLocalCache")
  private HashLocalCache<Set<String>> headerVariableMapLocalCache;

  @Mock
  private ConfigChangeEventPublisher configChangeEventHandler;

  @InjectMocks
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testUpdateHeaderTemplateCache() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().businessProcessId("bpId1").template("template1").build();
    headerTemplateCacheHelper.updateHeaderTemplateCache(headerTemplate);
    verify(headerTemplateCache).put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE),
        "bpId1", "template1");
  }

  @Test
  void testUpdateHeaderEntityMapCache() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().businessProcessId("bpId1").template("template1").build();
    String previousTemplate = "oldTemplate";
    List<String> entityIds = Arrays.asList("entity1", "entity2");
    when(headerEntityMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    List<String> removedEntities =
        headerTemplateCacheHelper.updateHeaderEntityMapCache(headerTemplate, previousTemplate,
            entityIds);
    verify(headerEntityMapCache, times(2)).put(anyString(), anyString(), any(Set.class));
    assertNotNull(removedEntities);
  }

  @Test
  void testRemoveHeaderEntityMapCache() {
    String headerEntityMapCacheKey =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_ENTITY_MAP);
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().businessProcessId("bpId1").template("template1").build();
    List<HtmlExtractData> previousParsedTemplate = Collections.emptyList();
    List<String> entityIds = Arrays.asList("entity1", "entity2");
    when(headerEntityMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>(Collections.singletonList("bpId1")));
    List<String> removedEntityIds =
        headerTemplateCacheHelper.removeHeaderEntityMapCache(previousParsedTemplate, entityIds,
            headerEntityMapCacheKey, headerTemplate.getBusinessProcessId());
    assertTrue(removedEntityIds.isEmpty());
  }

  @Test
  void testUpdateHeaderVariableMapCache() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().businessProcessId("bpId1").template("template1").build();
    String previousTemplate = "oldTemplate";
    List<String> variableIds = Arrays.asList("var1", "var2");
    when(headerVariableMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    List<String> removedVariables =
        headerTemplateCacheHelper.updateHeaderVariableMapCache(headerTemplate, previousTemplate,
            variableIds);
    verify(headerVariableMapCache, times(2)).put(anyString(), anyString(), any(Set.class));
    assertNotNull(removedVariables);
  }

  @Test
  void testRemoveHeaderVariableMapCache() {
    String headerVariableMapCacheKey =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_VARIABLE_MAP);
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().businessProcessId("bpId1").template("template1").build();
    List<HtmlExtractData> previousParsedTemplate = Collections.emptyList();
    List<String> variableIds = Arrays.asList("var1", "var2");
    when(headerVariableMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>(Collections.singletonList("bpId1")));
    List<String> removedList = headerTemplateCacheHelper.removeHeaderVariableMapCache(previousParsedTemplate, variableIds,
        headerVariableMapCacheKey, headerTemplate.getBusinessProcessId());
    assertTrue(removedList.isEmpty());
  }

  @Test
  void testSetHeaderTemplate() {
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().businessProcessId("bpId1").template("template1").build();
    List<HtmlExtractData> htmlExtractedData = Collections.emptyList();
    when(headerTemplateCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        "oldTemplate");
    headerTemplateCacheHelper.setHeaderTemplate(headerTemplate, htmlExtractedData);
    verify(headerTemplateCache).put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE),
        "bpId1", "template1");
  }

  @Test
  void testGetHeaderTemplateEntityBpMap() {
    String entityId = "entity1";
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_ENTITY_MAP);
    Set<String> expectedSet = new LinkedHashSet<>(Collections.singletonList("bpId1"));
    when(headerEntityMapLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(headerEntityMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        expectedSet);
    Set<String> result = headerTemplateCacheHelper.getHeaderTemplateEntityBpMap(entityId);
    assertEquals(expectedSet, result);
  }

  @Test
  void testGetHeaderTemplateVariableBpMap() {
    String variableId = "variable1";
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_VARIABLE_MAP);
    Set<String> expectedSet = new LinkedHashSet<>(Collections.singletonList("bpId1"));
    when(headerVariableMapLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(headerVariableMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        expectedSet);
    Set<String> result = headerTemplateCacheHelper.getHeaderTemplateVariableBpMap(variableId);
    assertEquals(expectedSet, result);
  }

  @Test
  void testGetHeaderTemplate() {
    String businessProcessId = "bpId1";
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE);
    String expectedTemplate = "template1";
    when(headerTemplateLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(headerTemplateCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        expectedTemplate);
    String result = headerTemplateCacheHelper.getHeaderTemplate(businessProcessId);
    assertEquals(expectedTemplate, result);
  }
}
