package com.uniphore.uassist.summary.dao;

import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GenAISummaryConfigDao {

  @Autowired
  private GenAISummaryConfigRepo genAISummaryConfigRepo;

  @Autowired
  private GenAISummaryConfigCacheHelper genAISummaryConfigCache;

  public GenAISummaryConfigDoc upsertGenAISummaryConfig(
      final GenAISummaryConfig genAISummaryConfig) {
    log.debug(
        "upsert GenAISummaryConfig to DB with tenantId : {}, environment : {} experienceId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), genAISummaryConfig.getExperienceId());
    String timestamp = String.valueOf(DateUtil.getTimestamp());
    return genAISummaryConfigRepo.save(DocConverter.toGenAISummaryConfigDoc(
        this.genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
            genAISummaryConfig.getExperienceId(), TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()), genAISummaryConfig, timestamp));
  }

  public GenAISummaryConfig fetchGenAISummaryConfigByExpId(final String experienceId) {
    log.debug("Fetch GenAISummaryConfig from DB with experienceId : {}", experienceId);
    return DocConverter.toGenAISummaryConfig(
        genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
            experienceId, TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public void copyGenAISummaryConfigToTargetEnvironment(final String tenantId,
      final String sourceEnv, final String targetEnv, final String userId) {
    this.genAISummaryConfigRepo.deleteGenAISummaryConfigByTenantIdAndEnvironment(tenantId,
        targetEnv);
    List<GenAISummaryConfigDoc> genAISummaryConfigDocs =
        this.genAISummaryConfigRepo.findGenAISummaryConfigByTenantIdAndEnvironment(tenantId,
            sourceEnv);
    this.genAISummaryConfigRepo.saveAll(
        genAISummaryConfigDocs.stream().map(genAISummaryConfigDoc -> {
          genAISummaryConfigDoc.setEnvironment(targetEnv);
          genAISummaryConfigDoc.setUserId(userId);
          genAISummaryConfigDoc.setModifiedTimestamp(DateUtil.getTimestamp());
          genAISummaryConfigDoc.setId(null);
          return genAISummaryConfigDoc;
        }).collect(Collectors.toList()));

    this.genAISummaryConfigCache.remove(targetEnv);
    for (GenAISummaryConfigDoc doc : genAISummaryConfigDocs) {
      GenAISummaryConfig config = DocConverter.toGenAISummaryConfig(Optional.ofNullable(doc));
      this.genAISummaryConfigCache.setForDeployment(config, targetEnv);
    }
  }
}
