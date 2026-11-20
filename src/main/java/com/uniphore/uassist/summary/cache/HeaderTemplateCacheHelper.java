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
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;

@Component
public class HeaderTemplateCacheHelper {

  @Autowired
  private CacheOperations<String> headerTemplateCache;

  @Autowired
  private CacheOperations<Set<String>> headerEntityMapCache;

  @Autowired
  private CacheOperations<Set<String>> headerVariableMapCache;

  @Autowired
  @Qualifier("headerTemplateLocalCache")
  private HashLocalCache<String> headerTemplateLocalCache;

  @Autowired
  @Qualifier("headerEntityMapLocalCache")
  private HashLocalCache<Set<String>> headerEntityMapLocalCache;

  @Autowired
  @Qualifier("headerVariableMapLocalCache")
  private HashLocalCache<Set<String>> headerVariableMapLocalCache;

  @Autowired
  private ConfigChangeEventPublisher configChangeEventHandler;

  public void updateHeaderTemplateCache(HeaderTemplate headerTemplate) {
    headerTemplateCache.put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE),
        headerTemplate.getBusinessProcessId(), headerTemplate.getTemplate());
  }

  public List<String> updateHeaderEntityMapCache(HeaderTemplate headerTemplate,
      String previousTemplate, List<String> entityIds) {
    List<String> removeEntityIds = null;
    String headerEntityMapCacheKey =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_ENTITY_MAP);

    if (StringUtils.isNotEmpty(previousTemplate)) {
      removeEntityIds = removeHeaderEntityMapCache(HtmlParser.extract(previousTemplate), entityIds,
          headerEntityMapCacheKey, headerTemplate.getBusinessProcessId());
    }
    entityIds.forEach(entityId -> {
      final Set<String> previousBpIds =
          Optional.ofNullable(this.headerEntityMapCache.get(headerEntityMapCacheKey, entityId,
              new TypeReference<Set<String>>() {})).orElse(new LinkedHashSet<>());
      previousBpIds.add(headerTemplate.getBusinessProcessId());
      this.headerEntityMapCache.put(headerEntityMapCacheKey, entityId, previousBpIds);
    });
    return removeEntityIds;
  }

  List<String> removeHeaderEntityMapCache(List<HtmlExtractData> previousParsedTemplate,
      List<String> entityIds, String headerEntityMapCacheKey, String bpId) {
    List<String> entIdsToBeRemoved = new ArrayList<>((CollectionUtils
        .removeAll(HtmlParser.entORVarIds(previousParsedTemplate, HtmlParser.ENTITY), entityIds)));
    entIdsToBeRemoved.forEach(entityId -> {
      final Set<String> previousBpIds =
          Optional.ofNullable(this.headerEntityMapCache.get(headerEntityMapCacheKey, entityId,
              new TypeReference<Set<String>>() {})).orElse(new LinkedHashSet<>());
      previousBpIds.remove(bpId);
      this.headerEntityMapCache.put(headerEntityMapCacheKey, entityId, previousBpIds);
    });
    return entIdsToBeRemoved;
  }

  public List<String> updateHeaderVariableMapCache(HeaderTemplate headerTemplate,
      String previousTemplate, List<String> variableIds) {
    List<String> removeVariableIds = null;
    String headerVariableMapCacheKey =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_VARIABLE_MAP);

    if (StringUtils.isNotEmpty(previousTemplate)) {
      removeVariableIds = removeHeaderVariableMapCache(HtmlParser.extract(previousTemplate),
          variableIds, headerVariableMapCacheKey, headerTemplate.getBusinessProcessId());
    }
    variableIds.forEach(variableId -> {
      final Set<String> previousBpIds =
          Optional.ofNullable(this.headerVariableMapCache.get(headerVariableMapCacheKey, variableId,
              new TypeReference<Set<String>>() {})).orElse(new LinkedHashSet<>());
      previousBpIds.add(headerTemplate.getBusinessProcessId());
      this.headerVariableMapCache.put(headerVariableMapCacheKey, variableId, previousBpIds);
    });
    return removeVariableIds;
  }

  List<String> removeHeaderVariableMapCache(List<HtmlExtractData> previousParsedTemplate,
      List<String> variableIds, String headerVariableMapCacheKey, String bpId) {
    List<String> varIdsToBeRemoved = new ArrayList<>((CollectionUtils.removeAll(
        HtmlParser.entORVarIds(previousParsedTemplate, HtmlParser.VARIABLE), variableIds)));
    varIdsToBeRemoved.forEach(variableId -> {
      final Set<String> previousBpIds =
          Optional.ofNullable(this.headerVariableMapCache.get(headerVariableMapCacheKey, variableId,
              new TypeReference<Set<String>>() {})).orElse(new LinkedHashSet<>());
      previousBpIds.remove(bpId);
      this.headerVariableMapCache.put(headerVariableMapCacheKey, variableId, previousBpIds);

    });
    return varIdsToBeRemoved;
  }

  public void setHeaderTemplate(HeaderTemplate headerTemplate,
      List<HtmlExtractData> htmlExtractedData) {
    /* fetching previousTemplate before updating headerTemplate cache */
    String previousTemplate = this.headerTemplateCache.get(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE),
        headerTemplate.getBusinessProcessId(), new TypeReference<String>() {});

    List<String> addEntityIds = HtmlParser.entORVarIds(htmlExtractedData, HtmlParser.ENTITY);
    List<String> addVariableIds = HtmlParser.entORVarIds(htmlExtractedData, HtmlParser.VARIABLE);
    this.updateHeaderTemplateCache(headerTemplate);
    List<String> removeEntityIds =
        this.updateHeaderEntityMapCache(headerTemplate, previousTemplate, addEntityIds);
    List<String> removeVariableIds =
        this.updateHeaderVariableMapCache(headerTemplate, previousTemplate, addVariableIds);

    configChangeEventHandler.publishDeleteMessage(headerTemplate.getBusinessProcessId(),
        Constants.HEADER, TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(),
        Commons.combineListsToSet(addEntityIds, removeEntityIds),
        Commons.combineListsToSet(addVariableIds, removeVariableIds));
  }

  public Set<String> getHeaderTemplateEntityBpMap(final String entityId) {
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_ENTITY_MAP);
    Set<String> headerEntityMap = this.headerEntityMapLocalCache.get(key, entityId);
    if (headerEntityMap == null) {
      headerEntityMap = Optional
          .ofNullable(
              this.headerEntityMapCache.get(key, entityId, new TypeReference<Set<String>>() {}))
          .orElse(new LinkedHashSet<>());
      this.headerEntityMapLocalCache.put(key, entityId, headerEntityMap);
    }

    return headerEntityMap;
  }

  public Set<String> getHeaderTemplateVariableBpMap(final String variableId) {
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_VARIABLE_MAP);
    Set<String> headerVariableMap = this.headerVariableMapLocalCache.get(key, variableId);

    if (headerVariableMap == null) {
      headerVariableMap = Optional
          .ofNullable(
              this.headerVariableMapCache.get(key, variableId, new TypeReference<Set<String>>() {}))
          .orElse(new LinkedHashSet<>());
      this.headerVariableMapLocalCache.put(key, variableId, headerVariableMap);
    }
    return headerVariableMap;
  }

  public String getHeaderTemplate(String businessProcessId) {
    String key =
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE);
    String template = this.headerTemplateLocalCache.get(key, businessProcessId);
    if (StringUtils.isEmpty(template)) {
      template =
          this.headerTemplateCache.get(key, businessProcessId, new TypeReference<String>() {});
      this.headerTemplateLocalCache.put(key, businessProcessId, template);
    }
    return template;
  }

}
