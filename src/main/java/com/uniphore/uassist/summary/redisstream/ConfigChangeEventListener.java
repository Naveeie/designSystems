package com.uniphore.uassist.summary.redisstream;

import java.net.InetAddress;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.cache.CommonCacheHelper;
import com.uniphore.uassist.summary.cache.HashLocalCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConfigChangeEventListener
    implements StreamListener<byte[], MapRecord<byte[], byte[], byte[]>> {

  @Value("${redis.stream.config-change-key:uassist-config-change}")
  private String streamKey;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Autowired
  @Qualifier("dispositionLocalCache")
  private HashLocalCache<Disposition> dispositionLocalCache;

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
  @Qualifier("intentTemplateLocalCache")
  private HashLocalCache<String> intentTemplateLocalCache;

  @Autowired
  @Qualifier("intentEntityMapLocalCache")
  private HashLocalCache<Set<String>> intentEntityMapLocalCache;

  @Autowired
  @Qualifier("intentVariableMapLocalCache")
  private HashLocalCache<Set<String>> intentVariableMapLocalCache;

  @Autowired
  @Qualifier("genAISummaryConfigLocalCache")
  private HashLocalCache<GenAISummaryConfig> genAISummaryConfigLocalCache;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  @SneakyThrows
  public void onMessage(MapRecord<byte[], byte[], byte[]> record) {
    try {
      String message = record.getValue().entrySet().stream()
          .filter(
              (e) -> ConfigChangeEventPublisher.MESSAGE.equalsIgnoreCase(new String(e.getKey())))
          .findFirst().map(v -> new String(v.getValue())).orElse(StringUtils.EMPTY);
      log.debug("OnMessage:: {}", message);
      if (StringUtils.isNotEmpty(message)) {
        ConfigChangeEvent configChangeEvent =
            objectMapper.readValue(message, ConfigChangeEvent.class);

        log.debug("ConfigChangeEventListener host: {}  - consumed message:{} ",
            InetAddress.getLocalHost().getHostName(), configChangeEvent);
        if (configChangeEvent != null) {
          this.updateConfigFromLocalCache(configChangeEvent, configChangeEvent.getEventType());
          redisTemplate.getConnectionFactory().getConnection().xAck(streamKey.getBytes(), streamKey,
              record.getId());
        }
      }
    } catch (Exception e) {
      log.error("Failed to the message from redis stream", e);
    }
  }

  private void updateConfigFromLocalCache(ConfigChangeEvent configChangeEvent, String eventType) {
    TenantContext.setTenantContext(configChangeEvent.getTenantId(), configChangeEvent.getEnv());
    if (Constants.DISPOSITION.equals(eventType)) {
      String key = CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_DISPOSITION);
      dispositionLocalCache.delete(key, configChangeEvent.getBusinessProcessId());
    } else if (Constants.HEADER.equals(eventType)) {
      String headerCacheKey =
          CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_TEMPLATE);
      if (StringUtils.isNotEmpty(
          headerTemplateLocalCache.get(headerCacheKey, configChangeEvent.getId()))) {
        this.headerTemplateLocalCache.delete(headerCacheKey,
            configChangeEvent.getId());
        this.headerEntityMapLocalCache.deleteAll(
            CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_ENTITY_MAP),
            configChangeEvent.getRemoveEntityIds());
        this.headerVariableMapLocalCache.deleteAll(
            CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_HEADER_VARIABLE_MAP),
            configChangeEvent.getRemoveVariableIds());
      }

    } else if (Constants.INTENT.equals(eventType)) {
      String intentCacheKey =
          CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_TEMPLATE);
      if (StringUtils
          .isNotEmpty(intentTemplateLocalCache.get(intentCacheKey, configChangeEvent.getId()))) {
        this.intentTemplateLocalCache.delete(intentCacheKey, configChangeEvent.getId());
        this.intentEntityMapLocalCache.deleteAll(
            CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_ENTITY_MAP),
            configChangeEvent.getRemoveEntityIds());
        this.intentVariableMapLocalCache.deleteAll(
            CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_INTENT_VARIABLE_MAP),
            configChangeEvent.getRemoveVariableIds());
      }
    } else if (Constants.GEN_AI_SUMMARY.equals(eventType)) {
      String key =
          CommonCacheHelper.getCommonCacheKey(CommonCacheHelper.CACHE_PREFIX_GENAISUMMARYCONFIG);
      this.genAISummaryConfigLocalCache.delete(key, configChangeEvent.getExperienceId());
    }

  }

}
