package com.uniphore.uassist.summary.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.HeaderTemplateCacheHelper;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.HeaderTemplateDao;
import com.uniphore.uassist.summary.dao.documents.HeaderTemplateDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.model.MessageResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HeaderTemplateServiceImpl implements HeaderTemplateService {

  @Autowired
  private HeaderTemplateDao headerTemplateDao;

  @Autowired
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  @Override
  public MessageResponse upsertHeaderTemplate(final HeaderTemplate headerTemplate) {
    log.info("Saving Header Template for tenantId : {}, environment : {} and headerTemplate : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), headerTemplate);
    HeaderTemplateDoc headerTemplateDoc;
    try {
      final List<HtmlExtractData> htmlExtractDatas =
          HtmlParser.extract(headerTemplate.getTemplate());

      headerTemplateDoc = this.headerTemplateDao.upsertHeaderTemplate(headerTemplate);
      /**
       * If Header Template saved successfully then we are pushing data to the cache for Runtime
       * activity.
       */
      if (Objects.nonNull(headerTemplateDoc)) {
        this.headerTemplateCacheHelper.setHeaderTemplate(headerTemplate, htmlExtractDatas);
        return new MessageResponse(Constants.SUCCESS, headerTemplateDoc.getFieldId());
      }
    } catch (Exception e) {
      log.error("Execption while saving Header Template for tenantId : {}, environment : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
    return new MessageResponse(Constants.FAILURE, Constants.EMPTY);
  }

  @Override
  public HeaderTemplate fetchHeaderTemplate(final String businessProcessId) {
    log.info("Get Header Template with tenantId : {}, environment : {} and businessProcessId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), businessProcessId);
    HeaderTemplate headerTemplate = null;
    try {
      headerTemplate = this.headerTemplateDao.fetchHeaderTemplate(businessProcessId);
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Header Template with tenantId : {}, environment : {}, businessProcessId : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), businessProcessId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
    return headerTemplate;
  }

  @Override
  public List<HeaderTemplate> fetchHeaderTemplates(final List<String> businessProcessIds) {
    log.info("Get Intent Template with tenant : {}, environment : {} and businessProcessIds : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), businessProcessIds);
    List<HeaderTemplate> headerTemplates = new ArrayList<>();
    try {
      headerTemplates = this.headerTemplateDao.fetchHeaderTemplates(businessProcessIds);
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Header Template with tenantId : {}, environment : {}, businessProcessIds : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), businessProcessIds, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
    return headerTemplates;
  }

}
