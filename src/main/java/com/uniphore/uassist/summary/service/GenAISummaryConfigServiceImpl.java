package com.uniphore.uassist.summary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigDao;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class GenAISummaryConfigServiceImpl implements GenAISummaryConfigService {

  @Autowired
  private GenAISummaryConfigDao genAISummaryConfigDao;

  @Autowired
  private GenAISummaryConfigCacheHelper genAISummaryConfigCache;

  private final com.fasterxml.jackson.databind.ObjectMapper ObjectMapper = new ObjectMapper();

  @Override
  public GenAISummaryConfig upsertGenAISummaryConfig(
      final GenAISummaryConfig genAISummaryConfigReq) {
    log.info(
        "upsert GenAISummaryConfig with tenantId : {}, environment : {} and genAISummaryConfig : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), genAISummaryConfigReq);
    GenAISummaryConfigDoc genAISummaryConfigDoc;
    try {
      genAISummaryConfigDoc =
          this.genAISummaryConfigDao.upsertGenAISummaryConfig(genAISummaryConfigReq);
      if (Objects.nonNull(genAISummaryConfigDoc)) {
        GenAISummaryConfig sumConf =
            DocConverter.toGenAISummaryConfig(Optional.ofNullable(genAISummaryConfigDoc));
        this.genAISummaryConfigCache.set(sumConf);
        return sumConf;
      }
    } catch (Exception e) {
      log.error(
          "Execption occured while saving GenAISummaryConfig for tenant : {}, environment : {}",
          genAISummaryConfigReq.getTenantId(), genAISummaryConfigReq.getEnvironment(), e);
      throw new CustomException(ErrorCodes.GENAISUMMARYCONFIG_NOT_CREATED,
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return null;
  }

  @Override
  public GenAISummaryConfig getGenAISummaryConfig(final String expId) {
    log.info("Get GenAISummaryConfig with tenantId : {}, environment : {} and experienceId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), expId);
    GenAISummaryConfig genAISummaryConfig = null;
    try {
      genAISummaryConfig = this.genAISummaryConfigCache.find(expId);
      log.info("Get the data from cache for exp {} with value {}", expId, genAISummaryConfig);
      if (Objects.isNull(genAISummaryConfig)) {
        genAISummaryConfig = this.genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(expId);
      }
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching GenAISummaryConfig with tenant : {}, environment : {}, experienceId : {}, error : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), expId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return genAISummaryConfig;
  }

  @Override
  public Boolean getIsOnDemand(final String expId) {
    log.info(
        "Get IsOnDemand Enabled for GenAISummaryConfig with tenantId : {}, environment : {} and experienceId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), expId);
    GenAISummaryConfig genAISummaryConfig = null;
    try {
      genAISummaryConfig = this.genAISummaryConfigCache.find(expId);
      if (Objects.isNull(genAISummaryConfig)) {
        genAISummaryConfig = this.genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(expId);
      }

      if (Objects.isNull(genAISummaryConfig)) {
        return false;
      } else {
        return convertDocToGenAISummaryCache(genAISummaryConfig).isOnDemandSummaryEnabled();
      }
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching GenAISummaryConfig OnDemand Config with tenant : {}, environment : {}, experienceId : {}, error : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), expId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public Map<String, Boolean> validateEntityUsedInGenAISummary(
      final List<String> entityIdsToValidate, final String expId) {
    Map<String, Boolean> entityUsedInGenAISummary = new HashMap<>();
    try {
      final GenAISummaryConfig genAISummaryConfig =
          this.genAISummaryConfigDao.fetchGenAISummaryConfigByExpId(expId);
      if (Objects.nonNull(genAISummaryConfig)) {
        if (StringUtils.isNotEmpty(genAISummaryConfig.getConfig())) {
          final GenAISummaryCache genAISummaryCache =
              this.convertDocToGenAISummaryCache(genAISummaryConfig);
          Optional.ofNullable(genAISummaryCache).ifPresent(summaryCache -> {
            summaryCache.getSectionMap().forEach((key, value) -> value.stream()
                .filter(entity -> entityIdsToValidate.contains(entity.getEntityId())).findFirst()
                .ifPresent(entity -> entityUsedInGenAISummary.put(entity.getEntityId(), true)));
          });
        }
      }
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching GenAISummaryConfig Config with tenant : {}, environment : {}, experienceId : {} error: {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), expId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return entityUsedInGenAISummary;
  }

  private GenAISummaryCache convertDocToGenAISummaryCache(final GenAISummaryConfig genAISummaryConfig)
      throws JsonProcessingException {
    return ObjectMapper.readValue(genAISummaryConfig.getConfig(), new TypeReference<GenAISummaryCache>() {});
  }
}
