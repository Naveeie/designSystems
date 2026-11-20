package com.uniphore.uassist.summary.cache;

import java.util.Map;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;

public class CommonCacheHelper {

  private CommonCacheHelper() {}

  public static final String CACHE_PREFIX_SUMMARY = "uniphore.summary";
  public static final String CACHE_PREFIX_AGENT_STEPS = "uniphore.agent-steps";
  public static final String CACHE_PREFIX_SUMMARY_CONF = "uniphore.summary.config";
  public static final String CACHE_PREFIX_INTENT_TEMPLATE = "intent-template";
  public static final String CACHE_PREFIX_INTENT_ENTITY_MAP = "intent-entity.map";
  public static final String CACHE_PREFIX_INTENT_VARIABLE_MAP = "intent-variable.map";
  public static final String CACHE_PREFIX_DISPOSITION = "disposition";
  public static final String CACHE_PREFIX_GENAISUMMARYCONFIG = "genAISummaryConfig";
  public static final String CACHE_PREFIX_GENAI_SUMMARY_REQUESTED = "uniphore.genaisummary.requested";
  public static final String CACHE_KEY_FORMAT = "%s.%s";

  public static final String CACHE_PREFIX_HEADER_ENTITY_MAP = "header-entity.map";
  public static final String CACHE_PREFIX_HEADER_VARIABLE_MAP = "header-variable.map";
  public static final String CACHE_PREFIX_HEADER_TEMPLATE = "header-template";
  public static final String CACHE_PREFIX_DYNAMIC_REFRESH = "uniphore.summary.cache-refresh";

  public static String getSummaryCacheKey() {
    return TenantConstant.getKeyName(TenantContext.getMap(), CACHE_PREFIX_SUMMARY);
  }

  public static String getAgentStepsCacheKey() {
    return TenantConstant.getKeyName(TenantContext.getMap(), CACHE_PREFIX_AGENT_STEPS);
  }

  public static String getGenAISummaryCacheKey() {
    return TenantConstant.getKeyName(TenantContext.getMap(), CACHE_PREFIX_GENAI_SUMMARY_REQUESTED);
  }
  
  /* Instance: uniphore.summary.config.<tenant-id>.<environment>.intent-template */
  public static String getCommonCacheKey(final String type) {
    String keyName = TenantConstant.getKeyName(TenantContext.getMap(), CACHE_PREFIX_SUMMARY_CONF);
    return String.format(CACHE_KEY_FORMAT, keyName, type);

  }

  /* Instance: uniphore.summary.config.<tenant-id>.<environment>.intent-template */
  public static String getCommonCacheKeyforDeployment(final String type,
      Map<String, String> tenantMap) {
    String keyName = TenantConstant.getKeyName(tenantMap, CACHE_PREFIX_SUMMARY_CONF);
    return String.format(CACHE_KEY_FORMAT, keyName, type);
  }

  public static String getUserCacheKey(final String type) {
    String keyName = TenantConstant.getKeyName(TenantContext.getMap(), CACHE_PREFIX_SUMMARY);
    return String.format(CACHE_KEY_FORMAT, keyName, type);
  }

  public static String getRefreshCacheKey(final String type) {
    String keyName =
        TenantConstant.getKeyName(TenantContext.getMap(), CACHE_PREFIX_DYNAMIC_REFRESH);
    return String.format(CACHE_KEY_FORMAT, keyName, type);
  }

}
