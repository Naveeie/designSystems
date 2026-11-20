package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.uniphore.uassist.summary.cache.DispositionCacheHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.dao.documents.DispositionDoc;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.DispositionLevels;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DispositionDao {

  @Autowired
  private DispositionRepo dispositionRepo;

  @Autowired
  private DispositionCacheHelper dispositionCache;

  public DispositionDoc upsertDisposition(final Disposition disposition) {
    log.debug(
        "upsert Disposition to DB with tenantId : {}, environment : {} businessProcessId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), disposition.getBusinessProcessId());
    String timestamp = String.valueOf(DateUtil.getTimestamp());
    return dispositionRepo.save(DocConverter.toDispositionDoc(
        this.dispositionRepo.findDispositionByBusinessProcessIdAndTenantIdAndEnvironment(
            disposition.getBusinessProcessId(), TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()), disposition, timestamp));
  }

  public Disposition fetchDispositionByBusinessProcess(final String businessProcessId) {
    log.debug("Fetch Disposition from DB with businessProcessId : {}", businessProcessId);
    return DocConverter.toDisposition(
        dispositionRepo.findDispositionByBusinessProcessIdAndTenantIdAndEnvironment(
            businessProcessId, TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public List<DispositionLevels> fetchDispositionLevels(final List<String> businessProcessIds) {
    log.debug("Fetch Disposition Levels from DB with businessProcessIds : {}", businessProcessIds);
    return DocConverter.toDispositionLevels(
        dispositionRepo.findDispositionByBusinessProcessIdInAndTenantIdAndEnvironment(
            businessProcessIds, TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public void copyDispositionsToTargetEnvironment(final String tenantId, final String sourceEnv,
      final String targetEnv, final String userId) {
    this.dispositionRepo.deleteDispositionByTenantIdAndEnvironment(tenantId, targetEnv);
    List<DispositionDoc> dispositionDocs =
        this.dispositionRepo.findDispositionByTenantIdAndEnvironment(tenantId, sourceEnv);
    List<DispositionDoc> targetDispositions = dispositionDocs.stream().map(dispositionDoc -> {
      dispositionDoc.setEnvironment(targetEnv);
      dispositionDoc.setUserId(userId);
      dispositionDoc.setModifiedTimestamp(DateUtil.getTimestamp());
      dispositionDoc.setId(null);
      return dispositionDoc;
    }).collect(Collectors.toList());
    this.dispositionRepo.saveAll(targetDispositions);
    targetDispositions.forEach(d -> {
      this.dispositionCache.set(DocConverter.toDisposition(Optional.ofNullable(d)));
    });
  }
}
