package com.uniphore.uassist.summary.integrationtests.cucumberglue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.cache.GenAISummaryConfigCacheHelper;
import com.uniphore.uassist.summary.dao.GenAISummaryConfigRepo;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.service.GenAISummaryConfigServiceImpl;
import com.uniphore.uassist.summary.service.SummaryRuntimeServiceImpl;
import com.uniphore.uassist.summary.utils.TestConstant;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
@SpringBootTest
@ActiveProfiles({TestConstant.PROFILE_NAME, TestConstant.PROFILE_REDIS, TestConstant.PROFILE_MONGO,
    TestConstant.PROFILE_KAFKA})
@Slf4j
public class GenAiConfigurationTest {

  private static final String GEN_AI_CONFIG_JSON = "gen-ai-config.json";
  private static final String GEN_AI_CONFIG_UPDATE_JSON = "gen-ai-config-update.json";
  private static final String GEN_AI_CONFIG_DELETE_JSON = "gen-ai-config-delete.json";
  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";
  private static final String USERNAME = "dev";

  @MockBean
  private RestTemplate restTemplate;

  private MockMvc mockMvc;

  @Autowired
  private LaunchDarklyHelper launchDarklyHelper;

  @MockBean
  private CollectorRegistry collectorRegistry;

  @MockBean
  private SummaryRuntimeServiceImpl summaryRuntimeService;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private GenAISummaryConfigServiceImpl genAISummaryConfigService;

  @Autowired
  private GenAISummaryConfigCacheHelper genAISummaryConfigCacheHelper;

  @Autowired
  private GenAISummaryConfigRepo genAISummaryConfigRepo;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockBean
  private OpenTelemetry openTelemetry;

  private String tenantId;
  private String experience;
  private String sessionId;
  private String conversationId;
  private String config;
  private ResultActions resultActions;
  private List<String> entityIds;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
  }

  @Given("tenant id {string} and experience {string}")
  public void givenTenantIdAndExperience(String tenantId, String experience) {
    this.tenantId = tenantId;
    this.experience = experience;
    mockTenantContext(tenantId, "dev");
  }

  @Given(
      "tenant id {string} and conversation id {string} and experience {string} and session id {string}")
  public void givenTenantIdAndExperienceAndExternalConversationId(String tenantId,
      String conversationId, String experience, String sessionId) {
    this.tenantId = tenantId;
    this.experience = experience;
    this.conversationId = conversationId;
    this.sessionId = sessionId;
    mockTenantContext(tenantId, "dev");
  }

  @When("creating gen-ai configuration")
  public void createGenAiConfiguration() {
    try {
      GenAISummaryConfig genAISummaryConfig =
          this.inputGenAISummaryConfigResponse(GEN_AI_CONFIG_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/upsertGenAISummaryConfig").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(genAISummaryConfig))
              .contentType(MediaType.APPLICATION_JSON));
      this.config = genAISummaryConfig.getConfig();
    } catch (Exception e) {
      log.error("create gen ai config failed: ", e);
    }
  }

  @Then("response should be {int}")
  public void responseShouldBe(int response) {
    Assert.assertEquals(response, resultActions.andReturn().getResponse().getStatus());
  }

  @Then("gen-ai configuration should contain tenant id {string} and environment {string}")
  public void genAiConfigCheck(String tenantId, String environment)
      throws UnsupportedEncodingException {
    String response = resultActions.andReturn().getResponse().getContentAsString();
    Assert.assertTrue(response.contains(tenantId));
    Assert.assertTrue(response.contains("dev"));
  }

  @Then("gen-ai configuration should be created")
  public void genAiConfigurationShouldBeCreated() {
    Optional<GenAISummaryConfigDoc> createdConfig =
        this.genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
            this.experience, this.tenantId, "dev");
    Assert.assertEquals(this.config, createdConfig.get().getConfig());
  }

  @Then("gen-ai configuration should be stored in cache")
  public void genAiConfigurationInCache() {
    String createdConfig =
        this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(this.experience).get()
            .getSummaryFormat().toString();
    String actualConfig =
        "GenAISummaryCache.SummaryFormat(callReason=bullet-point, agentSteps=number-list, outcome=paragraph)";
    Assert.assertEquals(actualConfig, createdConfig);
  }

  @When("fetching gen-ai summary configuration with experience {string}")
  public void getGenAiSummaryConfig(String experience) {
    try {
      resultActions = this.mockMvc.perform(
          get("/v1/getGenAISumConfig").param("experienceId", experience)
              .contentType(MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      log.error("getting gen-ai summary configuration failed: ", e);
    }
  }

  @When("updating gen-ai configuration")
  public void updateGenAiConfiguration() {
    try {
      GenAISummaryConfig genAISummaryConfig =
          this.inputGenAISummaryConfigResponse(GEN_AI_CONFIG_UPDATE_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/upsertGenAISummaryConfig").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(genAISummaryConfig))
              .contentType(MediaType.APPLICATION_JSON));
      this.config = genAISummaryConfig.getConfig();
    } catch (Exception e) {
      log.error("update gen ai config failed: ", e);
    }
  }

  @When("deleting gen-ai configuration")
  public void deleteGenAiConfiguration() {
    try {
      GenAISummaryConfig genAISummaryConfig =
          this.inputGenAISummaryConfigResponse(GEN_AI_CONFIG_DELETE_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/upsertGenAISummaryConfig").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(genAISummaryConfig))
              .contentType(MediaType.APPLICATION_JSON));
      this.config = genAISummaryConfig.getConfig();
    } catch (Exception e) {
      log.error("delete gen ai config failed: ", e);
    }
  }

  @Then("gen-ai configuration should be updated")
  public void genAiConfigurationShouldBeUpdated() {
    Optional<GenAISummaryConfigDoc> updatedConfig =
        this.genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
            this.experience, this.tenantId, "dev");
    Assert.assertEquals(this.config, updatedConfig.get().getConfig());
  }

  @Then("gen-ai configuration should be updated in cache")
  public void genAiConfigurationUpdatedInCache() {
    String updatedConfig =
        this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(this.experience).get()
            .getSummaryFormat().toString();
    String actualConfig =
        "GenAISummaryCache.SummaryFormat(callReason=bullet-point, agentSteps=number-list, outcome=paragraph)";
    Assert.assertEquals(actualConfig, updatedConfig);
  }

  @Then("gen-ai configuration should be deleted")
  public void genAiConfigurationShouldBeDeleted() {
    Optional<GenAISummaryConfigDoc> deletedConfig =
        this.genAISummaryConfigRepo.findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
            this.experience, this.tenantId, "dev");
    Assert.assertEquals(this.config, deletedConfig.get().getConfig());
  }

  @Then("gen-ai configuration should be deleted from cache")
  public void genAiConfigurationDeletedFromCache() {
    Optional<GenAISummaryCache> deletedConfig =
        this.genAISummaryConfigCacheHelper.getGenAIConfigCacheByExperience(this.experience);
    Assert.assertFalse(deletedConfig.isPresent());
  }

  @Given("entity IDs {string} and experience {string}")
  public void givenEntityIdsAndExperienceId(String entityIds, String experience) {
    this.entityIds = Arrays.asList(entityIds.split(","));
    this.experience = experience;
  }

  @When("validating entities under gen-ai summary")
  public void validateEntityUsageInGenAiSummary() {
    try {
      resultActions = this.mockMvc.perform(
          get("/v1/entityUsedInGenAi").param("entityIds", entityIds.toArray(new String[0]))
              .param("experienceId", Optional.ofNullable(experience).orElse(""))
              .contentType(MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      log.error("gen-ai summary entity validation failed: ", e);
    }
  }

  @When("fetching gen-ai summary target language")
  public void getGenAiSummaryTargetLanguage() {
    try {
      resultActions = this.mockMvc.perform(
          get("/v1/genAISummaryTargetLanguage").contentType(MediaType.APPLICATION_JSON));
    } catch (Exception e) {
      log.error("getting gen-ai summary target language failed: ", e);
    }
  }

  private GenAISummaryConfig inputGenAISummaryConfigResponse(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfig>() {
        });
  }

  public void mockTenantContext(final String tenantId, final String environment) {
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, tenantId);
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, environment);
    TenantContext.setMap(tenantMap);
  }
}
