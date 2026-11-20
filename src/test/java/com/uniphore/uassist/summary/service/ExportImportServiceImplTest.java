package com.uniphore.uassist.summary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.environments.v1.Dependencies;
import com.uniphore.environments.v1.ExportResponse;
import com.uniphore.environments.v1.ImportResponse;
import com.uniphore.environments.v1.RollbackResponse;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigRepo;
import com.uniphore.uassist.summary.kafka.MessagePublisher;
import com.uniphore.uassist.summary.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class ExportImportServiceImplTest {

  @InjectMocks
  private ExportImportServiceImpl service;

  @Mock
  private MessagePublisher dataStreamPublisher;
  @Mock
  private HeaderTemplateService headerTemplateService;
  @Mock
  private IntentTemplateService intentTemplateService;
  @Mock
  private GenAISummaryConfigService genAISummaryConfigService;
  @Mock
  private PlatformService platformService;
  @Mock
  private GenAISummaryConfigRepo genAISummaryConfigRepo;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    TenantContext.setTenantContext("testTenant", "dev");
    ReflectionTestUtils.setField(service, "mapper", new ObjectMapper());
  }

  @Test
  void testExportSummaryInThread_success() throws Exception {
    String experienceId = "exp1";
    String requestId = "req1";
    Map<String, String> categoryMap = Map.of("catFieldName", "bp1");
    HeaderTemplate ht = new HeaderTemplate();
    List<IntentTemplate> its = List.of(new IntentTemplate());
    GenAISummaryConfig config = new GenAISummaryConfig();
    when(platformService.getCategoryMap(anyString(), anyString())).thenReturn(categoryMap);
    when(headerTemplateService.fetchHeaderTemplate(anyString())).thenReturn(ht);
    when(intentTemplateService.getIntentTemplatesByExpId(anyString())).thenReturn(its);
    when(genAISummaryConfigService.getGenAISummaryConfig(anyString())).thenReturn(config);
    service.exportSummaryInThread(experienceId, requestId);
    verify(dataStreamPublisher, atLeastOnce()).publishExportSummary(any(ExportResponse.class));
  }

  @Test
  void testRollbackSummaryInThread_success() {
    ExportImportSummary summary = ExportImportSummary.builder().headerTemplate(new HeaderTemplate())
        .intentTemplate(List.of(new IntentTemplate())).genAISummaryConfig(new GenAISummaryConfig())
        .build();
    service.rollbackSummaryInThread(summary, "exp1", "req1");
    verify(dataStreamPublisher, atLeastOnce()).publishRollbackSummary(any(RollbackResponse.class));
  }

  @Test
  void testRollbackSummaryInThread_exception() {
    ExportImportSummary summary = ExportImportSummary.builder().headerTemplate(new HeaderTemplate())
        .intentTemplate(List.of(new IntentTemplate())).genAISummaryConfig(new GenAISummaryConfig())
        .build();
    doThrow(new RuntimeException("Test exception")).when(headerTemplateService)
        .upsertHeaderTemplate(any());
    service.rollbackSummaryInThread(summary, "exp1", "req1");
  }

  @Test
  void testExtractExportDependencies() throws Exception {
    IntentTemplate intent = new IntentTemplate();
    intent.setIntentId("intent1");
    intent.setTemplate("entity-id=\"entity1\" variable-id=\"var1\"");
    HeaderTemplate header = new HeaderTemplate();
    header.setTemplate("entity-id=\"entity2\" variable-id=\"var2\"");
    GenAISummaryConfig config = new GenAISummaryConfig();
    config.setConfig(
        "{\"sectionMap\":{\"sec1\":[{\"entityId\":\"entity3\",\"variableId\":\"var3\"}]}}");
    ExportImportSummary summary =
        ExportImportSummary.builder().headerTemplate(header).intentTemplate(List.of(intent))
            .genAISummaryConfig(config).build();
    List<Dependencies> deps = service.extractExportDependencies(summary, "req1");
    assertFalse(deps.isEmpty());
  }

  @Test
  void testImportSummaryInThread_success() throws Exception {
    ImportSummaryRequest req = mock(ImportSummaryRequest.class);
    when(req.getExperienceId()).thenReturn("exp1");
    when(req.getRequestId()).thenReturn("req1");
    String configDataJson =
        "{\"configData\":{\"headerTemplate\":{},\"intentTemplate\":[],\"genAISummaryConfig\":{}}}";
    when(req.getConfig()).thenReturn(new ObjectMapper().readTree(configDataJson));
    when(req.getDependencies()).thenReturn(new HashMap<>());
    Map<String, String> categoryMap = Map.of("catFieldName", "bp1");
    when(platformService.getCategoryMap(anyString(), anyString())).thenReturn(categoryMap);
    when(headerTemplateService.fetchHeaderTemplate(anyString())).thenReturn(new HeaderTemplate());
    when(intentTemplateService.getIntentTemplatesByExpId(anyString())).thenReturn(List.of());
    when(genAISummaryConfigService.getGenAISummaryConfig(anyString())).thenReturn(
        new GenAISummaryConfig());
    service.importSummaryInThread(req, "token");
    verify(dataStreamPublisher, atLeastOnce()).publishImportSummary(any(ImportResponse.class));
  }
  
  @Test
  void testReplaceImportDependencies() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree("{\"id\":\"oldId\"}");
    Map<String, List<ImportSummaryRequest.Dependency>> deps = new HashMap<>();
    ImportSummaryRequest.Dependency dep = mock(ImportSummaryRequest.Dependency.class);
    when(dep.getOldId()).thenReturn("oldId");
    when(dep.getNewId()).thenReturn("newId");
    deps.put("test", List.of(dep));
    JsonNode result = service.replaceImportDependencies(node, deps, "req1");
    assertEquals("newId", result.get("id").asText());
  }
}
