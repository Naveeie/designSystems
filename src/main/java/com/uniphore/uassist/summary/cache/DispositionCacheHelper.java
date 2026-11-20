package com.uniphore.uassist.summary.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.redisstream.ConfigChangeEventPublisher;

import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DispositionCacheHelper {

  @Autowired
  private CacheOperations<Disposition> dispositionCache;
  @Autowired
  @Qualifier("dispositionLocalCache")
  private HashLocalCache<Disposition> dispositionLocalCache;

  @Autowired
  private ConfigChangeEventPublisher configChangeEventHandler;

  public void set(final Disposition disposition) {
    log.debug(
        "Save the Disposition to cache tenantId: {}, environment : {}, businessProcessId: {} ",
        disposition.getTenantId(), disposition.getEnvironment(),
        disposition.getBusinessProcessId());

    this.dispositionCache.put(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION),
        disposition.getBusinessProcessId(), disposition);
    this.configChangeEventHandler.publishDeleteMessage(disposition.getDispositionId(),
        Constants.DISPOSITION, disposition.getBusinessProcessId(), disposition.getTenantId(),
        disposition.getEnvironment(),StringUtil.EMPTY_STRING);
  }

  public Disposition find(final String businessProcessId) {
    String key = CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION);
    Disposition disposition = this.dispositionLocalCache.get(key, businessProcessId);
    if (disposition == null) {
      disposition = this.dispositionCache.get(
          CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION),
          businessProcessId, new TypeReference<Disposition>() {});
      this.dispositionLocalCache.put(key, businessProcessId, disposition);
    }
    return disposition;
  }

  public void remove(final String businessProcessId) {
    String tenantId = TenantContext.getMap().get(TenantConstant.META_KEY_TENANT_ID);
    String env = TenantContext.getMap().get(TenantConstant.META_KEY_ENVIRONMENT);
    log.info("remove the Summary to cache tenantId: {} , environment :{}, businessProcessId :{} ",
        tenantId,env, businessProcessId);
    this.dispositionCache.delete(
        CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION),
        businessProcessId);
    this.configChangeEventHandler.publishDeleteMessage(businessProcessId,
        Constants.DISPOSITION, businessProcessId, tenantId,env,StringUtil.EMPTY_STRING);
  }

}
