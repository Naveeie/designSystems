package com.uniphore.uassist.summary.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.CacheRefreshHelper;
import com.uniphore.uassist.summary.cache.DispositionCacheHelper;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.DispositionDao;
import com.uniphore.uassist.summary.dao.documents.DispositionDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.CacheRefreshBean;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.model.DispositionLevels;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.model.MessageResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DispositionServiceImpl implements DispositionService {

  @Autowired
  private DispositionDao dispositionDao;

  @Autowired
  private DispositionCacheHelper dispositionCache;

  @Override
  public MessageResponse upsertDisposition(final Disposition dispositionReq) {
    log.info("upsert Disposition with tenantId : {}, environment : {} and disposition : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), dispositionReq);
    DispositionDoc dispositionDoc;
    try {
      dispositionDoc = this.dispositionDao.upsertDisposition(dispositionReq);
      if (Objects.nonNull(dispositionDoc)) {
        this.dispositionCache.set(DocConverter.toDisposition(Optional.ofNullable(dispositionDoc)));
        return new MessageResponse(Constants.SUCCESS, dispositionDoc.getFieldId());
      }
    } catch (Exception e) {
      log.error("Execption occured while saving Disposition for tenant : {}, environment : {}",
          dispositionReq.getTenantId(), dispositionReq.getEnvironment(), e);
      throw new CustomException(ErrorCodes.TEMPLATE_NOT_CREATED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new MessageResponse(Constants.FAILURE, Constants.EMPTY);
  }

  @Override
  public Disposition getDisposition(final String businessProcessId) {
    log.info("Get Disposition with tenantId : {}, environment : {} and businessProcessId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), businessProcessId);
    Disposition disposition = null;
    try {
      disposition = this.dispositionCache.find(businessProcessId);
      if (Objects.isNull(disposition)) {
        disposition = this.dispositionDao.fetchDispositionByBusinessProcess(businessProcessId);
      }

      if (Objects.isNull(disposition)) {
        disposition = Disposition.builder().name(StringUtils.EMPTY).values(new HashMap<>())
            .tenantId(TenantUtils.getTenantDetails().getTenantId())
            .environment(TenantUtils.getTenantDetails().getEnvironment())
            .businessProcessId(businessProcessId).description(StringUtils.EMPTY).build();
      }
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Disposition with tenant : {}, environment : {}, businessProccessId : {}, error : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), businessProcessId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return disposition;
  }

  @Override
  public List<DispositionLevels> getDispositionLevels(final List<String> businessProcessIds) {
    log.info(
        "Get Disposition Levels with tenant : {}, environment : {} and businessProcessIds : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), businessProcessIds);
    List<DispositionLevels> dispLevels = new ArrayList<>();
    try {
      dispLevels = this.dispositionDao.fetchDispositionLevels(businessProcessIds);
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Disposition Levels with tenant : {}, environment : {}, disposition : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), businessProcessIds, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return dispLevels;
  }

}
