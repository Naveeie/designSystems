package com.uniphore.uassist.summary.cache;

import java.util.Objects;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.ri.common.tenant.TenantUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SummaryCacheHelper {

  @Value("${app.redis.summary-cache.ttl:3600000}")
  private long cachettl;

  @Autowired
  private CacheOperations<SummaryDoc> summaryCache;

  @Autowired
  private CacheOperations<Boolean> genAIsummaryCache;

  public void set(final SummaryDoc summaryDoc) {
    log.debug("Save the Summary to cache tenantId :{}, environment :{}, conversationId :{}, summaryDoc :{}",
        summaryDoc.getTenantId(), summaryDoc.getEnvironment(), summaryDoc.getConversationId(),
        summaryDoc);
    String key = CommonCacheHelper.getSummaryCacheKey();
    log.debug("redis key for summary -rt :{}",key);
    this.summaryCache.put(key, summaryDoc.getConversationId(), summaryDoc, cachettl);
  }

  public SummaryDoc find(final String conversationId) {
    return this.summaryCache.get(CommonCacheHelper.getSummaryCacheKey(), conversationId,
        new TypeReference<SummaryDoc>() {
        });
  }

  public void remove(final String conversationId) {
    log.debug("remove the Summary to cache tenantId :{} , environment {}, conversationId :{} ",
        TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID),
        TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT), conversationId);
    String key = CommonCacheHelper.getSummaryCacheKey();
    log.debug("deleting this conversationId:{} for this key :{}", conversationId, key);
    this.summaryCache.delete(key, conversationId);
  }

  /**
   * Returns false is someone is trying to acquire lock.
   * Returns true if someone else already holds the lock.
   */
  public boolean acquireGenAiSummaryGenerationLock(final String conversationId) {
    final String key = CommonCacheHelper.getGenAISummaryCacheKey();
    log.debug("Checking if lock is already acquired for key: {} and conversationId: {}", key,
        conversationId);
    final Boolean lockAcquired =
        this.genAIsummaryCache.get(key, conversationId, new TypeReference<Boolean>() {
        });
    if (Objects.isNull(lockAcquired)) {
      log.debug("Making a lock acquire for conversationId: {}", conversationId);
      this.genAIsummaryCache.put(key, conversationId, Boolean.TRUE, cachettl);
      return Boolean.FALSE;
    }
    return lockAcquired;
  }

  public void removeGenAIRequested(final String conversationId) {
    log.debug(
        "remove the createGenAiSummary ondemand from cache tenantId :{} , environment {}, conversationId :{} ",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), conversationId);
    String key = CommonCacheHelper.getGenAISummaryCacheKey();
    this.genAIsummaryCache.delete(key, conversationId);
  }

}
