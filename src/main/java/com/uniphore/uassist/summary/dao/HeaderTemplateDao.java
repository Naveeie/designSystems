package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.uniphore.uassist.summary.cache.HeaderTemplateCacheHelper;
import com.uniphore.uassist.summary.common.HtmlParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.dao.documents.HeaderTemplateDoc;
import com.uniphore.uassist.summary.model.DocConverter;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class HeaderTemplateDao {

  @Autowired
  private HeaderTemplateRepository headerTemplateRepo;

  @Autowired
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  public HeaderTemplateDoc upsertHeaderTemplate(final HeaderTemplate headerTemplate) {
    log.debug("upsert Header Template to DB with businessProcessId : {}",
        headerTemplate.getBusinessProcessId());
    String timestamp = DateUtil.getTimestamp();
    return this.headerTemplateRepo.save(DocConverter.toHeaderTemplateDoc(
        this.headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
            headerTemplate.getBusinessProcessId(), TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()), headerTemplate, timestamp));
  }

  public HeaderTemplate fetchHeaderTemplate(final String businessProcessId) {
    log.debug("Fetch Header Template from DB with businessProcessId : {}", businessProcessId);
    return DocConverter.toHeaderTemplate(
        this.headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
            businessProcessId, TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public List<HeaderTemplate> fetchHeaderTemplates(final List<String> businessProcessIds) {
    log.debug("Fetch Header Template from DB with businessProcessId : {}", businessProcessIds);
    return DocConverter.toHeaderTemplates(
        this.headerTemplateRepo.findHeaderTemplateByBusinessProcessIdInAndTenantIdAndEnvironment(
            businessProcessIds, TenantUtils.getTenantDetails().getTenantId(),
            TenantUtils.getTenantDetails().getEnvironment()));
  }

  public void copyHeaderTemplatesToTargetEnvironment(final String tenantId, final String sourceEnv,
      final String targetEnv, final String userId) {
    this.headerTemplateRepo.deleteHeaderTemplateByTenantIdAndEnvironment(tenantId, targetEnv);
    List<HeaderTemplateDoc> headerTemplateDocs =
        this.headerTemplateRepo.findHeaderTemplateByTenantIdAndEnvironment(tenantId, sourceEnv);
    log.debug("Header Teemplate found in sourceEnv: [{}]", headerTemplateDocs);
    List<HeaderTemplateDoc> headerTemplates = headerTemplateDocs.stream().map(headerTemplateDoc -> {
      headerTemplateDoc.setEnvironment(targetEnv);
      headerTemplateDoc.setUserId(userId);
      headerTemplateDoc.setModifiedTimestamp(DateUtil.getTimestamp());
      headerTemplateDoc.setId(null);
      return headerTemplateDoc;
    }).collect(Collectors.toList());
    this.headerTemplateRepo.saveAll(headerTemplates);

    //update cache
    headerTemplates.stream().forEach(t -> {
      this.headerTemplateCacheHelper.setHeaderTemplate(
          DocConverter.toHeaderTemplate(Optional.ofNullable(t)),
          HtmlParser.extract(t.getTemplate()));
    });
  }

}
