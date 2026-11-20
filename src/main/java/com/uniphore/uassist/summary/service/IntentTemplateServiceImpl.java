package com.uniphore.uassist.summary.service;

import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.IntentTemplateCacheHelper;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.IntentTemplateDao;
import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.model.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class IntentTemplateServiceImpl implements IntentTemplateService {

  @Autowired
  private IntentTemplateDao intentTemplateDao;

  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  @Override
  public MessageResponse upsertIntentTemplate(final IntentTemplate intentTemplateReq) {
    log.info("upsert Intent Template with tenantId : {}, environment : {} and intentTemplate : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), intentTemplateReq);
    IntentTemplateDoc intentTemplateDoc;
    try {
      final List<HtmlExtractData> htmlExtractedData = HtmlParser.extract(intentTemplateReq.getTemplate());
      intentTemplateDoc = this.intentTemplateDao.upsertIntentTemplate(intentTemplateReq);
      if (Objects.nonNull(intentTemplateDoc)) {
        this.intentTemplateCacheHelper.cacheIntentTemplate(intentTemplateReq, htmlExtractedData);
        return new MessageResponse(Constants.SUCCESS, intentTemplateDoc.getFieldId());
      }
    } catch (Exception e) {
      log.error(
          "Execption occured while saving Intent Template for tenantId : {}, environment : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), e);
      throw new CustomException(ErrorCodes.TEMPLATE_NOT_CREATED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new MessageResponse(Constants.FAILURE, Constants.EMPTY);
  }

  @Override
  public IntentTemplate getIntentTemplate(final String intentId) {
    log.info("Get Intent Template with tenantId : {}, environment : {} and intentId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), intentId);
    IntentTemplate intentTemplate = null;
    try {
      intentTemplate = this.intentTemplateDao.fetchIntentTemplate(intentId);
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Intent Template with tenantId : {}, environment : {}, intentId : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), intentId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return intentTemplate;
  }

  @Override
  public List<IntentTemplate> getIntentTemplates(final List<String> intentIds) {
    log.info("Get Intent Template with tenant : {}, environment : {} and intentIds : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), intentIds);
    List<IntentTemplate> intentTemplates = new ArrayList<>();
    try {
      intentTemplates = this.intentTemplateDao.fetchIntentTemplates(intentIds);
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Intent Templates with tenantId : {}, environment : {}, intentIds : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), intentIds, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return intentTemplates;
  }

  @Override
  public boolean isVariableUsedInSummary(String variableId) {
    log.info("Validate if variable is used in summary with tenantId : {}, environment : {} and variableId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), variableId);

    try {
      List<String> templates = this.intentTemplateDao.fetchTemplates();
      log.info("fetched templates: {}", templates);
      return templates.stream()
          .anyMatch(template -> template.contains(variableId));

    } catch (CustomException e) {
      log.error(
          "Exception occured while validating variables used in summary with tenantId : {}, environment : {}, variableId : {} with error: {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), variableId, e);
      throw e;
    } catch (Exception e) {
      log.error(
          "Exception occured while validating variables used in summary with tenantId : {}, environment : {}, variableId : {} with error: {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), variableId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
  
  @Override
  public List<IntentTemplate> getIntentTemplatesByExpId(final String expId) {
    log.info("Get Intent Template with tenantId : {}, environment : {} and expId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), expId);
    List<IntentTemplate> intentTemplate = null;
    try {
      intentTemplate = this.intentTemplateDao.fetchIntentTemplatesByExpId(expId);
    } catch (Exception e) {
      log.error(
          "Execption occured while fetching Intent Template with tenantId : {}, environment : {}, intentId : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), expId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return intentTemplate;
  }

  @Override
  public void deleteIntentTemplateByExpId(final String expId) {
    log.info("Delete Intent Template with tenantId : {}, environment : {} and expId : {}",
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment(), expId);
    try {
      this.intentTemplateDao.deleteIntentTemplatesByExpId(expId);
    } catch (Exception e) {
      log.error(
          "Execption occured while deleting Intent Template with tenantId : {}, environment : {}, expId : {}",
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), expId, e);
      throw new CustomException(ErrorCodes.SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
