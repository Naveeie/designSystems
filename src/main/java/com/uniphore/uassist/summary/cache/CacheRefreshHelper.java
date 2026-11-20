package com.uniphore.uassist.summary.cache;

import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.DispositionDao;
import com.uniphore.uassist.summary.model.CacheRefreshBean;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.service.HeaderTemplateService;
import com.uniphore.uassist.summary.service.IntentTemplateService;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Saroj Kumar
 *
 */
@Component
@Slf4j
public class CacheRefreshHelper {

  @Autowired
  private DispositionDao dispositionDao;

  @Autowired
  private DispositionCacheHelper dispositionCache;

  @Autowired
  private CacheOperations<Boolean> cacheOperations;

  @Autowired
  private HeaderTemplateService headerTemplateService;

  @Autowired
  private IntentTemplateService intentTemplateService;

  @Autowired
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @Value("${cache.refresh.ttl}")
  private int refreshCacheTtlTime;

  public String refreshHeaderTemplate(final CacheRefreshBean cacheRefreshBean) {
    String headerTemplate = StringUtils.EMPTY;
    if (!checkIfAlreadyRequestSent(Constants.HEADER_TEMPLATE_KEY,
        cacheRefreshBean.getBusinessProcessId())) {
      final HeaderTemplate headerTemplateObj =
          this.headerTemplateService.fetchHeaderTemplate(cacheRefreshBean.getBusinessProcessId());
      if (Objects.nonNull(headerTemplateObj)
          && StringUtils.isNotEmpty(headerTemplateObj.getTemplate())) {
        final List<HtmlExtractData> htmlExtractDatas =
            HtmlParser.extract(headerTemplateObj.getTemplate());
        this.headerTemplateCacheHelper.setHeaderTemplate(headerTemplateObj, htmlExtractDatas);
        headerTemplate = headerTemplateObj.getTemplate();
      }
    }
    return headerTemplate;
  }

  public String refreshIntentTemplate(final CacheRefreshBean cacheRefreshBean) {
    String intentTemplate = StringUtils.EMPTY;
    if (!checkIfAlreadyRequestSent(Constants.INTENT_TEMPLATE_KEY, cacheRefreshBean.getIntentId())) {
      final IntentTemplate intentTemplateObj =
          this.intentTemplateService.getIntentTemplate(cacheRefreshBean.getIntentId());
      if (Objects.nonNull(intentTemplateObj)
          && StringUtils.isNotEmpty(intentTemplateObj.getTemplate())) {
        final List<HtmlExtractData> htmlExtractDatas =
            HtmlParser.extract(intentTemplateObj.getTemplate());
        this.intentTemplateCacheHelper.cacheIntentTemplate(intentTemplateObj, htmlExtractDatas);
        intentTemplate = intentTemplateObj.getTemplate();
      }
    }
    return intentTemplate;
  }

  /**
   * 
   * @param eventType
   * @param id
   * @return will be based on if request is already sent for refreshing the cache then it will be
   *         returning true for given ttl time. After that key will be expired from the cache. id
   *         might be businessProcessid or intentId
   * 
   */
  boolean checkIfAlreadyRequestSent(final String eventType, final String id) {
    final Boolean lockAcquired = this.cacheOperations
        .get(CommonCacheHelper.getRefreshCacheKey(eventType), id, new TypeReference<Boolean>() {});
    if (Objects.isNull(lockAcquired)) {
      log.info("Making a lock acquire for eventType: {} and id: {}", eventType, id);
      this.cacheOperations.put(CommonCacheHelper.getRefreshCacheKey(eventType), id, Boolean.TRUE,
          refreshCacheTtlTime);
      return Boolean.FALSE;
    }
    return lockAcquired;
  }

}
