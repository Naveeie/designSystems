package com.uniphore.uassist.summary.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.cache.IntentTemplateCacheHelper;
import com.uniphore.uassist.summary.common.HtmlParser;
import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IntentTemplateDao {

  @Autowired
  private IntentTemplateRepo intentTemplateRepo;

  @Autowired
  private HeaderTemplateRepository headerTemplateRepo;

  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  public IntentTemplateDoc upsertIntentTemplate(final IntentTemplate intentTemplate) {
    log.debug("upsert Intent Template to DB with intentId : {}", intentTemplate.getIntentId());
    String timestamp = DateUtil.getTimestamp();
    return this.intentTemplateRepo
        .save(
            DocConverter
                .toIntentTemplateDoc(
                    this.intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment(
                        intentTemplate.getIntentId(), TenantUtils.getTenantDetails().getTenantId(),
                        TenantUtils.getTenantDetails().getEnvironment()),
                    intentTemplate, timestamp));
  }

  public IntentTemplate fetchIntentTemplate(final String intentId) {
    log.debug("Fetch Intent Template from DB with intentId : {}", intentId);
    return DocConverter.toIntentTemplate(
        this.intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment(intentId,
            TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public List<IntentTemplate> fetchIntentTemplates(final List<String> intentIds) {
    log.debug("Fetch Intent Template from DB with intentIds : {}", intentIds);
    return DocConverter.toIntentTemplates(
        this.intentTemplateRepo.findIntentTemplateByIntentIdInAndTenantIdAndEnvironment(intentIds,
            TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public void copyIntentTemplatesToTargetEnvironment(final String tenantId, final String sourceEnv,
      final String targetEnv, final String userId) {
    this.intentTemplateRepo.deleteIntentTemplateByTenantIdAndEnvironment(tenantId, targetEnv);
    List<IntentTemplateDoc> intentTemplateDocs =
        this.intentTemplateRepo.findIntentTemplateByTenantIdAndEnvironment(tenantId, sourceEnv);
    List<IntentTemplateDoc> targetIntentTemplates =
        intentTemplateDocs.stream().map(intentTemplateDoc -> {
          intentTemplateDoc.setEnvironment(targetEnv);
          intentTemplateDoc.setUserId(userId);
          intentTemplateDoc.setModifiedTimestamp(DateUtil.getTimestamp());
          intentTemplateDoc.setId(null);
          return intentTemplateDoc;
        }).collect(Collectors.toList());
    this.intentTemplateRepo.saveAll(targetIntentTemplates);
    targetIntentTemplates.forEach(t -> {
      this.intentTemplateCacheHelper.cacheIntentTemplate(
          DocConverter.toIntentTemplate(Optional.ofNullable(t)),
          HtmlParser.extract(t.getTemplate()));
    });

  }

  public List<String> fetchTemplates() {
    log.info("Fetch all templates of the summary from DB ");

    List<String> intentTemplates =
        this.intentTemplateRepo.findAllTemplateFields(TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment());

    List<String> headerTemplates =
        this.headerTemplateRepo.findAllTemplateFields(TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment());

    List<String> templates = new ArrayList<>();
    templates.addAll(intentTemplates);
    templates.addAll(headerTemplates);

    return templates;
  }

  public List<IntentTemplate> fetchIntentTemplatesByExpId(final String expId) {
    log.debug("Fetch Intent Template from DB with intentIds: {}", expId);
    return DocConverter.toIntentTemplates(this.intentTemplateRepo
        .findIntentTemplateByExperienceIdAndTenantIdAndEnvironmentAndDeletedFalse(expId,
            TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public void deleteIntentTemplatesByExpId(final String expId) {
    log.debug("Delete Intent Template from DB with expIds : {}", expId);
    this.intentTemplateRepo.deleteIntentTemplateByExperienceIdAndTenantIdAndEnvironment(expId,
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment());
  }
}
