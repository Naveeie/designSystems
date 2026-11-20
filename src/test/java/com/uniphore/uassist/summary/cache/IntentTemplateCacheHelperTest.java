package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntentTemplateCacheHelperTest {

  @Mock
  private CacheOperations<String> intentTemplateCache;

  @Mock
  private CacheOperations<Set<String>> intentEntityMapCache;

  @Mock
  private CacheOperations<Set<String>> intentVariableMapCache;

  @Mock
  private CacheOperations<String> intentBoundaryCache;

  @Mock
  @Qualifier("intentTemplateLocalCache")
  private HashLocalCache<String> intentTemplateLocalCache;

  @Mock
  @Qualifier("intentEntityMapLocalCache")
  private HashLocalCache<Set<String>> intentEntityMapLocalCache;

  @Mock
  @Qualifier("intentVariableMapLocalCache")
  private HashLocalCache<Set<String>> intentVariableMapLocalCache;

  @Mock
  private ConfigChangeEventPublisher configChangeEventHandler;

  @Value("${app.redis.intent-cache.ttl}")
  private long cacheTtl;

  @InjectMocks
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testUpdateIntentTemplateCache() {
    IntentTemplate intentTemplate = new IntentTemplate();
    intentTemplate.setIntentId("testIntentId");
    intentTemplate.setTemplate("testTemplate");
    intentTemplateCacheHelper.updateIntentTemplateCache(intentTemplate);
    verify(intentTemplateCache).put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_TEMPLATE),
        "testIntentId", "testTemplate");
  }

  @Test
  void testUpdateIntentEntityMapCache() {
    IntentTemplate intentTemplate = new IntentTemplate();
    intentTemplate.setIntentId("testIntentId");
    List<String> entityIds = List.of("entity1", "entity2");
    String previousTemplate = "previousTemplate";

    when(intentEntityMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    List<HtmlExtractData> mockExtractedData =
        List.of(new HtmlExtractData("entity1", HtmlParser.ENTITY, "val"),
            new HtmlExtractData("entity3", HtmlParser.ENTITY, "val"));

    List<String> removedEntities =
        intentTemplateCacheHelper.updateIntentEntityMapCache(intentTemplate, previousTemplate,
            entityIds);

    verify(intentEntityMapCache, times(2)).put(anyString(), anyString(), any(Set.class));
    assertNotNull(removedEntities);
  }

  @Test
  void testUpdateIntentVariableMapCache() {
    IntentTemplate intentTemplate = new IntentTemplate();
    intentTemplate.setIntentId("testIntentId");
    List<String> variableIds = List.of("var1", "var2");
    String previousTemplate = "<var1></var1><var3></var3>";
    List<HtmlExtractData> parsedPreviousTemplate =
        List.of(new HtmlExtractData("var1", "Variable", "str"),
            new HtmlExtractData("var3", "Variable", "str"));

    try (MockedStatic<HtmlParser> htmlParserMock = mockStatic(HtmlParser.class)) {
      when(HtmlParser.extract(previousTemplate)).thenReturn(parsedPreviousTemplate);
      when(HtmlParser.entORVarIds(parsedPreviousTemplate, HtmlParser.VARIABLE)).thenReturn(
          List.of("var1", "var3"));

      when(intentVariableMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
          new LinkedHashSet<>());

      List<String> removedVariables =
          intentTemplateCacheHelper.updateIntentVariableMapCache(intentTemplate, previousTemplate,
              variableIds);

      assertNotNull(removedVariables);
      assertEquals(1, removedVariables.size());
      assertEquals("var3", removedVariables.get(0));
    }
  }

  @Test
  void testCacheIntentTemplate() {
    IntentTemplate intentTemplate = new IntentTemplate();
    intentTemplate.setIntentId("testIntentId");
    intentTemplate.setTemplate("testTemplate");
    List<HtmlExtractData> htmlExtractedData =
        List.of(new HtmlExtractData("entity1", "variable1", "type"));
    when(intentTemplateCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        "previousTemplate");
    when(intentEntityMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    when(intentVariableMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    intentTemplateCacheHelper.cacheIntentTemplate(intentTemplate, htmlExtractedData);
    verify(intentTemplateCache).put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_TEMPLATE),
        "testIntentId", "testTemplate");
    verify(configChangeEventHandler).publishDeleteMessage(eq("testIntentId"), eq("INTENT"),
        eq("3e37631c-78ac-4137-8415-72dcd90b19fd"), eq("dev"), anySet(), anySet());
  }

  @Test
  void testGetIntentTemplateEntityIntentMap() {
    String entityId = "entity1";
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_ENTITY_MAP);
    when(intentEntityMapLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(intentEntityMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    Set<String> result = intentTemplateCacheHelper.getIntentTemplateEntityIntentMap(entityId);
    verify(intentEntityMapLocalCache).put(anyString(), anyString(), any(Set.class));
    assertNotNull(result);
  }

  @Test
  void testGetIntentTemplateVariableIntentMap() {
    String variableId = "var1";
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_VARIABLE_MAP);
    when(intentVariableMapLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(intentVariableMapCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        new LinkedHashSet<>());
    Set<String> result = intentTemplateCacheHelper.getIntentTemplateVariableIntentMap(variableId);
    verify(intentVariableMapLocalCache).put(anyString(), anyString(), any(Set.class));
    assertNotNull(result);
  }

  @Test
  void testGetIntentTemplate() {
    when(intentTemplateLocalCache.get(anyString(), anyString())).thenReturn(null);
    when(intentTemplateCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(
        "testTemplate");
    String template = intentTemplateCacheHelper.getIntentTemplate("testIntentId");
    assertEquals("testTemplate", template);
    verify(intentTemplateLocalCache).put(anyString(), anyString(), anyString());
  }

  @Test
  void testPushIntentToIntentBoundaryStack() {
    String conversationId = "conversation1";
    String intentInstanceId = "intentInstance1";
    String key = CommonCacheHelper.getCommonCacheKey(
        String.format("%s.%s", IntentTemplateCacheHelper.CURRENT_INTENT, conversationId));
    intentTemplateCacheHelper.pushIntentToIntentBoundaryStack(conversationId, intentInstanceId);
    verify(intentBoundaryCache).push(key, intentInstanceId, cacheTtl);
  }

  @Test
  void testPopIntentFromIntentBoundaryStack() throws Exception {
    String conversationId = "conversation1";
    String key = CommonCacheHelper.getCommonCacheKey(
        String.format("%s.%s", IntentTemplateCacheHelper.CURRENT_INTENT, conversationId));

    byte[] serializedData = "serializedIntentInstanceId".getBytes();
    when(intentBoundaryCache.pop(anyString(), any(TypeReference.class))).thenReturn(
        Arrays.toString(serializedData));

    intentTemplateCacheHelper.popIntentFromIntentBoundaryStack(conversationId);
  }

  @Test
  void testGetCurrentIntentForConversation() {
    String conversationId = "conversation1";
    String cacheKey = CommonCacheHelper.getCommonCacheKey(
        String.format("%s.%s", IntentTemplateCacheHelper.CURRENT_INTENT, conversationId));

    List<String> intents = List.of("intent1", "intent2");
    when(intentBoundaryCache.listValues(eq(cacheKey), any(TypeReference.class))).thenReturn(
        intents);

    String currentIntent =
        intentTemplateCacheHelper.getCurrentIntentForConversation(conversationId);
    assertEquals("intent1", currentIntent);

    when(intentBoundaryCache.listValues(eq(cacheKey), any(TypeReference.class))).thenReturn(
        Collections.emptyList());

    currentIntent = intentTemplateCacheHelper.getCurrentIntentForConversation(conversationId);
    assertEquals(StringUtils.EMPTY, currentIntent);

    when(intentBoundaryCache.listValues(eq(cacheKey), any(TypeReference.class))).thenReturn(null);

    currentIntent = intentTemplateCacheHelper.getCurrentIntentForConversation(conversationId);
    assertEquals(StringUtils.EMPTY, currentIntent);
  }

  @Test
  void testGetAllIntentBoundaryStack() {
    String conversationId = "conversation1";
    String cacheKey = CommonCacheHelper.getCommonCacheKey(
        String.format("%s.%s", IntentTemplateCacheHelper.CURRENT_INTENT, conversationId));

    List<String> intentBoundaryStack = List.of("intent1", "intent2", "intent3");
    when(intentBoundaryCache.listValues(eq(cacheKey), any(TypeReference.class))).thenReturn(
        intentBoundaryStack);

    List<String> result = intentTemplateCacheHelper.getAllIntentBoundaryStack(conversationId);
    assertEquals(intentBoundaryStack, result);

    when(intentBoundaryCache.listValues(eq(cacheKey), any(TypeReference.class))).thenReturn(
        Collections.emptyList());

    result = intentTemplateCacheHelper.getAllIntentBoundaryStack(conversationId);
    assertTrue(result.isEmpty());

    when(intentBoundaryCache.listValues(eq(cacheKey), any(TypeReference.class))).thenReturn(null);

    result = intentTemplateCacheHelper.getAllIntentBoundaryStack(conversationId);
    assertNull(result);
  }

  @Test
  void testDeleteIntentBoundaryCache() {
    String conversationId = "conversation1";
    String cacheKey = CommonCacheHelper.getCommonCacheKey(
        String.format("%s.%s", IntentTemplateCacheHelper.CURRENT_INTENT, conversationId));

    intentTemplateCacheHelper.deleteIntentBoundaryCache(conversationId);

    verify(intentBoundaryCache).delete(eq(cacheKey));
  }
}
