package com.uniphore.uassist.summary.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IntentTemplateCacheHelper {

  public static final String CURRENT_INTENT = "current.intent";
  @Autowired
  private CacheOperations<String> intentTemplateCache;

  @Autowired
  private CacheOperations<Set<String>> intentEntityMapCache;

  @Autowired
  private CacheOperations<Set<String>> intentVariableMapCache;

  @Autowired
  private CacheOperations<String> intentBoundaryCache;

  @Autowired
  @Qualifier("intentTemplateLocalCache")
  private HashLocalCache<String> intentTemplateLocalCache;

  @Autowired
  @Qualifier("intentEntityMapLocalCache")
  private HashLocalCache<Set<String>> intentEntityMapLocalCache;

  @Autowired
  @Qualifier("intentVariableMapLocalCache")
  private HashLocalCache<Set<String>> intentVariableMapLocalCache;

  @Autowired
  private ConfigChangeEventPublisher configChangeEventHandler;

  @Value("${app.redis.intent-cache.ttl}")
  private long cacheTtl;

  public void updateIntentTemplateCache(final IntentTemplate intentTemplate) {
    intentTemplateCache.put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_TEMPLATE),
        intentTemplate.getIntentId(), intentTemplate.getTemplate());
  }

  public List<String> updateIntentEntityMapCache(final IntentTemplate intentTemplate,
      final String previousTemplate, List<String> entityIds) {
    List<String> removeEntityIds = null;
    String intentEntityMapCacheKey =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_ENTITY_MAP);

    if (StringUtils.isNotEmpty(previousTemplate)) {
      removeEntityIds = removeIntentEntityMapCache(HtmlParser.extract(previousTemplate), entityIds,
          intentEntityMapCacheKey, intentTemplate.getIntentId());
    }
    entityIds.forEach(entityId -> {
      final Set<String> previousIntents = Optional.ofNullable(
          this.intentEntityMapCache.get(intentEntityMapCacheKey, entityId,
              new TypeReference<Set<String>>() {
              })).orElse(new LinkedHashSet<>());
      previousIntents.add(intentTemplate.getIntentId());
      this.intentEntityMapCache.put(intentEntityMapCacheKey, entityId, previousIntents);
    });
    return removeEntityIds;
  }

  private List<String> removeIntentEntityMapCache(
      final List<HtmlExtractData> previousParsedTemplate, final List<String> entityIds,
      final String intentEntityMapCacheKey, final String intentId) {
    List<String> entIdsToBeRemoved = new ArrayList<>((CollectionUtils.removeAll(
        HtmlParser.entORVarIds(previousParsedTemplate, HtmlParser.ENTITY), entityIds)));
    entIdsToBeRemoved.forEach(entityId -> {
      Set<String> set = Optional.ofNullable(
          this.intentEntityMapCache.get(intentEntityMapCacheKey, entityId,
              new TypeReference<Set<String>>() {
              })).orElse(new HashSet<String>());
      set.remove(intentId);
      this.intentEntityMapCache.put(intentEntityMapCacheKey, entityId, set);
    });
    return entIdsToBeRemoved;
  }

  public List<String> updateIntentVariableMapCache(final IntentTemplate intentTemplate,
      final String previousTemplate, final List<String> variableIds) {
    List<String> removeVariableIds = null;
    final String intentVariableMapCacheKey =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_VARIABLE_MAP);

    if (StringUtils.isNotEmpty(previousTemplate)) {
      removeVariableIds =
          removeIntentVariableMapCache(HtmlParser.extract(previousTemplate), variableIds,
              intentVariableMapCacheKey, intentTemplate.getIntentId());
    }
    variableIds.forEach(variableId -> {
      final Set<String> previousIntents = Optional.ofNullable(
          this.intentVariableMapCache.get(intentVariableMapCacheKey, variableId,
              new TypeReference<Set<String>>() {
              })).orElse(new LinkedHashSet<>());
      previousIntents.add(intentTemplate.getIntentId());
      this.intentVariableMapCache.put(intentVariableMapCacheKey, variableId, previousIntents);
    });
    return removeVariableIds;
  }

  private List<String> removeIntentVariableMapCache(
      final List<HtmlExtractData> previousParsedTemplate, final List<String> variableIds,
      final String intentVariableMapCacheKey, String intentId) {
    List<String> varIdsToBeRemoved = new ArrayList<>((CollectionUtils.removeAll(
        HtmlParser.entORVarIds(previousParsedTemplate, HtmlParser.VARIABLE), variableIds)));
    varIdsToBeRemoved.forEach(variableId -> {
      {
        Set<String> set = Optional.ofNullable(
            this.intentVariableMapCache.get(intentVariableMapCacheKey, variableId,
                new TypeReference<Set<String>>() {
                })).orElse(new HashSet<String>());
        set.remove(intentId);
        this.intentVariableMapCache.put(intentVariableMapCacheKey, variableId, set);
      }
    });
    return varIdsToBeRemoved;
  }

  public void cacheIntentTemplate(final IntentTemplate intentTemplate,
      final List<HtmlExtractData> htmlExtractedData) {
    /* fetching previousTemplate before updating intentTemplate cache */
    final String previousTemplate = this.intentTemplateCache.get(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_TEMPLATE),
        intentTemplate.getIntentId(), new TypeReference<String>() {
        });

    this.updateIntentTemplateCache(intentTemplate);
    List<String> addEntityIds = HtmlParser.entORVarIds(htmlExtractedData, HtmlParser.ENTITY);
    List<String> addVariableIds = HtmlParser.entORVarIds(htmlExtractedData, HtmlParser.VARIABLE);
    List<String> removeEntityIds =
        this.updateIntentEntityMapCache(intentTemplate, previousTemplate, addEntityIds);
    List<String> removeVariableIds =
        this.updateIntentVariableMapCache(intentTemplate, previousTemplate, addVariableIds);

    configChangeEventHandler.publishDeleteMessage(intentTemplate.getIntentId(), Constants.INTENT,
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(),
        Commons.combineListsToSet(addEntityIds, removeEntityIds),
        Commons.combineListsToSet(addVariableIds, removeVariableIds));
  }

  public Set<String> getIntentTemplateEntityIntentMap(final String entityId) {
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_ENTITY_MAP);
    Set<String> intentEntityMap = this.intentEntityMapLocalCache.get(key, entityId);

    if (intentEntityMap == null) {
      intentEntityMap = Optional.ofNullable(
          this.intentEntityMapCache.get(key, entityId, new TypeReference<Set<String>>() {
          })).orElse(new LinkedHashSet<>());
      this.intentEntityMapLocalCache.put(key, entityId, intentEntityMap);
    }

    return intentEntityMap;
  }

  public Set<String> getIntentTemplateVariableIntentMap(final String variableId) {

    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_VARIABLE_MAP);
    Set<String> intentVariableMap = this.intentVariableMapLocalCache.get(key, variableId);

    if (intentVariableMap == null) {
      intentVariableMap = Optional.ofNullable(
          this.intentVariableMapCache.get(key, variableId, new TypeReference<Set<String>>() {
          })).orElse(new LinkedHashSet<>());
      this.intentVariableMapLocalCache.put(key, variableId, intentVariableMap);
    }

    return intentVariableMap;
  }

  public String getIntentTemplate(final String intentId) {

    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_TEMPLATE);
    String template = this.intentTemplateLocalCache.get(key, intentId);
    if (StringUtils.isEmpty(template)) {
      template = this.intentTemplateCache.get(key, intentId, new TypeReference<String>() {
      });
      this.intentTemplateLocalCache.put(key, intentId, template);
    }
    return template;
  }

  public void pushIntentToIntentBoundaryStack(final String conversationId,
      final String intentInstanceId) {
    String key = getIntentBoundaryCacheKey(conversationId);
    log.debug("update current intent: convId: {}, intents: {}", conversationId, intentInstanceId);
    this.intentBoundaryCache.push(key, intentInstanceId, this.cacheTtl);
  }

  public String getCurrentIntentForConversation(final String conversationId) {

    List<String> intents =
        this.intentBoundaryCache.listValues(this.getIntentBoundaryCacheKey(conversationId),
            new TypeReference<String>() {
            });
    log.debug("current intents: convId: {}, intents: {}", conversationId, intents);
    if (intents != null && !intents.isEmpty()) {
      return intents.get(0);
    }

    return StringUtils.EMPTY;
  }

  public void popIntentFromIntentBoundaryStack(final String conversationId) {
    String key = getIntentBoundaryCacheKey(conversationId);
    this.intentBoundaryCache.pop(key, new TypeReference<String>() {
    });
  }

  public void deleteIntentBoundaryCache(final String conversationId) {
    String key = getIntentBoundaryCacheKey(conversationId);
    this.intentBoundaryCache.delete(key);
  }

  private String getIntentBoundaryCacheKey(final String conversationId) {
    return CommonCacheHelper.getCommonCacheKey(
        String.format("%s.%s", CURRENT_INTENT, conversationId));
  }

  public List<String> getAllIntentBoundaryStack(String conversationId) {
    String key = getIntentBoundaryCacheKey(conversationId);
    return this.intentBoundaryCache.listValues(this.getIntentBoundaryCacheKey(conversationId),
        new TypeReference<String>() {
        });
  }
}
