package com.uniphore.uassist.summary.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.environments.v1.Dependencies;
import com.uniphore.environments.v1.ExportResponse;
import com.uniphore.environments.v1.ImportResponse;
import com.uniphore.environments.v1.RollbackResponse;
import com.uniphore.platform.telemetry.constants.Constant;
import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigRepo;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import com.uniphore.uassist.summary.model.ExportImportSummary;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.ImportSummaryRequest;
import com.uniphore.uassist.summary.model.ImportSummaryRequest.Dependency;
import com.uniphore.uassist.summary.model.IntentTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ExportImportServiceImpl implements ExportImportService {

  @Autowired
  private MessagePublisher dataStreamPublisher;

  @Autowired
  private HeaderTemplateService headerTemplateService;

  @Autowired
  private IntentTemplateService intentTemplateService;

  @Autowired
  private GenAISummaryConfigService genAISummaryConfigService;

  @Autowired
  private PlatformService platformService;

  @Autowired
  private GenAISummaryConfigRepo genAISummaryConfigRepo;

  private static final String CAT_FIELD_NAME = "catFieldName";
  private static final String INTENTS = "intents";
  private static final String ENTITIES = "entities";
  private static final String VARIABLES = "variables";
  private static final String ENTITYID = "entityId";
  private static final String VARIABLEID = "variableId";
  private static final String SECTIONMAP = "sectionMap";

  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("variable-id\\s*=\\s*\"?([^\\s\">]+)\"?");
  private static final Pattern ENTITY_PATTERN =
      Pattern.compile("entity-id\\s*=\\s*\"?([^\\s\">]+)\"?");

  ObjectMapper mapper = new ObjectMapper();

  @Override
  public void exportSummaryInThread(String experienceId, String requestId) {

    ExportResponse exportResponse;
    try {
      log.info("Started Exporting Summary for experienceId: {}, requestId: {}", experienceId,
          requestId);

      String authToken = "";
      Map<String, String> categoryMap =
          this.platformService.getCategoryMap(experienceId, authToken);
      String businessProcessId = categoryMap.get(CAT_FIELD_NAME);

      HeaderTemplate ht = this.headerTemplateService.fetchHeaderTemplate(businessProcessId);
      List<IntentTemplate> its = this.intentTemplateService.getIntentTemplatesByExpId(experienceId);
      GenAISummaryConfig config = genAISummaryConfigService.getGenAISummaryConfig(experienceId);

      ExportImportSummary exportImportSummary = ExportImportSummary.builder().headerTemplate(ht)
          .intentTemplate(its).genAISummaryConfig(config).build();

      ObjectMapper mapper = new ObjectMapper();
      byte[] bytes = mapper.writeValueAsBytes(exportImportSummary);

      List<Dependencies> dependencies = extractExportDependencies(exportImportSummary, requestId);

      exportResponse =
          ExportResponse.newBuilder().setFeatureName(Constants.SUMMARY).setStatus(Constants.SUCCESS)
              .setRequestId(requestId).setConfigData(com.google.protobuf.ByteString.copyFrom(bytes))
              .addAllDependencies(dependencies).setError(Constant.EMPTY).build();
      dataStreamPublisher.publishExportSummary(exportResponse);

      log.info("Finished Exporting Summary for experienceId: {}, requestId: {}", experienceId,
          requestId);
      log.debug("Exporting Summary finished for experienceId: {}, requestId: {}, response {}",
          experienceId, requestId, exportResponse);
    } catch (Exception e) {
      exportResponse = ExportResponse.newBuilder().setFeatureName(Constants.SUMMARY)
          .setStatus(Constants.FAILED).setRequestId(requestId).setError(e.getMessage()).build();
      dataStreamPublisher.publishExportSummary(exportResponse);
      log.error("Exporting Summary error for experienceId: {}, requestId: {}, response {}",
          experienceId, requestId, exportResponse);
    }
    return;
  }

  @Transactional
  @Override
  public void rollbackSummaryInThread(ExportImportSummary req, String experienceId,
      String requestId) {
    RollbackResponse rollbackResponse;
    try {
      log.info("Started rollback summary request for experienceId: {}, requestId: {}", experienceId,
          requestId);

      this.intentTemplateService.deleteIntentTemplateByExpId(experienceId);

      this.headerTemplateService.upsertHeaderTemplate(req.getHeaderTemplate());

      for (IntentTemplate intentTemplate : req.getIntentTemplate()) {
        this.intentTemplateService.upsertIntentTemplate(intentTemplate);
      }
      this.genAISummaryConfigService.upsertGenAISummaryConfig(req.getGenAISummaryConfig());

      rollbackResponse = RollbackResponse.newBuilder().setFeatureName(Constants.SUMMARY)
          .setStatus(Constant.SUCCESS).setRequestId(requestId).setError(Constant.EMPTY).build();

      dataStreamPublisher.publishRollbackSummary(rollbackResponse);

      log.info("Finished rollback summary request for experienceId: {}, requestId: {}",
          experienceId, requestId);

      log.debug("Rollback Summary : import Respose for requestId: {}, response {}", requestId,
          rollbackResponse);
    } catch (Exception e) {
      rollbackResponse = RollbackResponse.newBuilder().setFeatureName(Constants.SUMMARY)
          .setStatus(Constant.FAILED).setRequestId(requestId).setError(e.getMessage()).build();
      dataStreamPublisher.publishRollbackSummary(rollbackResponse);
      log.error("Rollback Summary :Error import Respose for requestId: {}, response {}", requestId,
          rollbackResponse);
    }

    return;
  }

  public List<Dependencies> extractExportDependencies(ExportImportSummary exportImportSummary,
      String requestId) throws Exception {

    Set<String> intentList = new HashSet<>();
    Set<String> entityList = new HashSet<>();
    Set<String> variableList = new HashSet<>();

    // Extract from Intent Templates
    if (exportImportSummary.getIntentTemplate() != null) {
      for (IntentTemplate intentTemplate : exportImportSummary.getIntentTemplate()) {
        if (intentTemplate.getIntentId() != null) {
          intentList.add(intentTemplate.getIntentId());
        }
        if (intentTemplate.getTemplate() != null) {
          // Extract variable IDs from template
          Matcher variableMatcher = VARIABLE_PATTERN.matcher(intentTemplate.getTemplate());
          while (variableMatcher.find()) {
            variableList.add(variableMatcher.group(1));
          }
          // Extract entity IDs from template
          Matcher entityMatcher = ENTITY_PATTERN.matcher(intentTemplate.getTemplate());
          while (entityMatcher.find()) {
            entityList.add(entityMatcher.group(1));
          }
        }
      }
    }

    if (exportImportSummary.getHeaderTemplate() != null
        && exportImportSummary.getHeaderTemplate().getTemplate() != null) {
      // Extract variable IDs from header template
      Matcher variableMatcher =
          VARIABLE_PATTERN.matcher(exportImportSummary.getHeaderTemplate().getTemplate());
      while (variableMatcher.find()) {
        variableList.add(variableMatcher.group(1));
      }
      // Extract entity IDs from header template
      Matcher entityMatcher =
          ENTITY_PATTERN.matcher(exportImportSummary.getHeaderTemplate().getTemplate());
      while (entityMatcher.find()) {
        entityList.add(entityMatcher.group(1));
      }
    }

    // Extract from GenAI Summary Config JSON
    if (exportImportSummary.getGenAISummaryConfig() != null
        && exportImportSummary.getGenAISummaryConfig().getConfig() != null) {

      ObjectMapper mapper = new ObjectMapper();
      JsonNode config = mapper.readTree(exportImportSummary.getGenAISummaryConfig().getConfig());

      JsonNode sectionMap = config.get(SECTIONMAP);
      if (sectionMap != null && sectionMap.isObject()) {
        Iterator<String> fieldNames = sectionMap.fieldNames();
        while (fieldNames.hasNext()) {
          String section = fieldNames.next();
          JsonNode sectionArray = sectionMap.get(section);

          if (sectionArray.isArray()) {
            for (JsonNode item : sectionArray) {
              if (item.has(ENTITYID)) {
                entityList.add(item.get(ENTITYID).asText());
              }
              if (item.has(VARIABLEID)) {
                variableList.add(item.get(VARIABLEID).asText());
              }
            }
          }
        }
      }
    }

    // Build Dependencies List
    List<Dependencies> dependenciesList = new ArrayList<>();
    if (intentList.size() > 0) {
      dependenciesList.add(
          Dependencies.newBuilder().setFeatureType(INTENTS).addAllFeatureNames(intentList).build());
    }
    if (entityList.size() > 0) {
      dependenciesList.add(Dependencies.newBuilder().setFeatureType(ENTITIES)
          .addAllFeatureNames(entityList).build());
    }
    if (variableList.size() > 0) {
      dependenciesList.add(Dependencies.newBuilder().setFeatureType(VARIABLES)
          .addAllFeatureNames(variableList).build());
    }

    return dependenciesList;
  }

  @Override
  @Transactional
  public void importSummaryInThread(ImportSummaryRequest importRequest, final String authToken)
      throws Exception {

    ImportResponse importResponse;
    try {
      log.info("Started Importing Summary for experienceId: {}, requestId: {}",
          importRequest.getExperienceId(), importRequest.getRequestId());

      log.debug("Full Data Importing Summary for experienceId: {}, requestId: {}, {}",
          importRequest.getExperienceId(), importRequest.getRequestId(),importRequest);
      
      Map<String, String> categoryMap =
          this.platformService.getCategoryMap(importRequest.getExperienceId(), authToken);
      String businessProcessId = categoryMap.get(CAT_FIELD_NAME);

      // Export For Rollback extraction
      HeaderTemplate ht = this.headerTemplateService.fetchHeaderTemplate(businessProcessId);
      List<IntentTemplate> its =
          this.intentTemplateService.getIntentTemplatesByExpId(importRequest.getExperienceId());
      GenAISummaryConfig config =
          genAISummaryConfigService.getGenAISummaryConfig(importRequest.getExperienceId());

      ExportImportSummary rollbackData = ExportImportSummary.builder().genAISummaryConfig(config)
          .headerTemplate(ht).intentTemplate(its).build();

      byte[] rollbackBytes = mapper.writeValueAsBytes(rollbackData);
      // End Export For Rollback extraction
      
      JsonNode configData = importRequest.getConfig();// Export File
      JsonNode summaryConfigJsonNode = configData.get("configData");
      JsonNode exportImportSummaryReplaced = replaceImportDependencies(summaryConfigJsonNode,
          importRequest.getDependencies(), importRequest.getRequestId());

      ExportImportSummary exportImportSummary =
          mapper.convertValue(exportImportSummaryReplaced, ExportImportSummary.class);

      HeaderTemplate headerTemplate = exportImportSummary.getHeaderTemplate();
      headerTemplate.setBusinessProcessId(businessProcessId);
      this.headerTemplateService.upsertHeaderTemplate(headerTemplate);

      this.intentTemplateService.deleteIntentTemplateByExpId(importRequest.getExperienceId());
      for (IntentTemplate intentTemplate : exportImportSummary.getIntentTemplate()) {
        intentTemplate.setExperienceId(importRequest.getExperienceId());
        intentTemplate.setBusinessProcessId(businessProcessId);
        this.intentTemplateService.upsertIntentTemplate(intentTemplate);
      }

      this.genAISummaryConfigRepo.deleteGenAISummaryConfigByTenantIdAndEnvironmentAndExperienceId(
          TenantUtils.getTenantDetails().getTenantId(),
          TenantUtils.getTenantDetails().getEnvironment(), importRequest.getExperienceId());
      GenAISummaryConfig genAISummaryConfig = exportImportSummary.getGenAISummaryConfig();
      genAISummaryConfig.setExperienceId(importRequest.getExperienceId());
      this.genAISummaryConfigService.upsertGenAISummaryConfig(genAISummaryConfig);

      importResponse = ImportResponse.newBuilder().setFeatureName(Constants.SUMMARY)
          .setStatus(Constants.SUCCESS).setRequestId(importRequest.getRequestId())
          .setBackupData(com.google.protobuf.ByteString.copyFrom(rollbackBytes))
          .setIsRollBackRequest(false).setError(Constant.EMPTY).build();
      dataStreamPublisher.publishImportSummary(importResponse);

      log.info("Finished Importing Summary for experienceId: {}, requestId: {}",
          importRequest.getExperienceId(), importRequest.getRequestId());
      log.debug("Importing Summary finished for experienceId: {}, requestId: {}, response {}",
          importRequest.getExperienceId(), importRequest.getRequestId(), importResponse);
    } catch (Exception e) {
      importResponse = ImportResponse.newBuilder().setFeatureName(Constants.SUMMARY)
          .setStatus(Constants.FAILED).setRequestId(importRequest.getRequestId())
          .setError(e.getMessage()).setIsRollBackRequest(false).build();
      dataStreamPublisher.publishImportSummary(importResponse);
      log.error("Importing Summary error for experienceId: {}, requestId: {}, response {}",
          importRequest.getExperienceId(), importRequest.getRequestId(), importResponse);
    }
    return;
  }

  public JsonNode replaceImportDependencies(JsonNode configs,
      Map<String, List<Dependency>> dependencies, String requestId) throws Exception {

    // Convert object to JSON string
    String json = mapper.writeValueAsString(configs);
    
    log.debug("before input value config {} and depedencies {} for requestId {}",
        json, dependencies, requestId);

    for (Map.Entry<String, List<ImportSummaryRequest.Dependency>> entry : dependencies.entrySet()) {
      List<ImportSummaryRequest.Dependency> dependencyList = entry.getValue();

      for (ImportSummaryRequest.Dependency dep : dependencyList) {
        String oldId = dep.getOldId();
        String newId = dep.getNewId();

        // Replace old ID with new ID in the JSON string
        json = json.replace(oldId, newId);
      }
    }

    log.debug("after input value config {} and depedencies {} for requestId {}",
        json, dependencies, requestId);
    return mapper.readTree(json);
  }
}
