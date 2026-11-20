package com.uniphore.uassist.summary.integrationtests.cucumberglue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.cache.IntentTemplateCacheHelper;
import com.uniphore.uassist.summary.dao.IntentTemplateRepo;
import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;
import com.uniphore.uassist.summary.model.IntentTemplate;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
@SpringBootTest

@Slf4j
public class IntentTemplateApplicationContextTest {

  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";
  private static final String INTENT_TEMPLATE_REQUEST_JSON = "intent-template-request.json";
  private static final String INTENT_TEMPLATE_REQUEST_UPDATE_JSON =
      "intent-template-request-update.json";
  private static final String INTENT_TEMPLATE_REQUEST_DELETE_JSON =
      "intent-template-request-delete.json";
  private static final String USERNAME = "test-user";

  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private IntentTemplateRepo intentTemplateRepo;

  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ResultActions resultActions;

  private String tenantId;
  private String experience;
  private String bpId;
  private String environment;
  private String template;
  private String userId;
  private String intentId;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
  }

  @Given(
      "tenantId {string} and experience {string} and business process {string} and environment {string}")
  public void givenTenantIdAndExpAndBpAndEnv(String tenantId, String experience, String bpId,
      String environment) {
    log.info("given tenantId : {}, experience : {}, bpId : {}, environment : {}", tenantId,
        experience, bpId, environment);
    this.tenantId = tenantId;
    this.experience = experience;
    this.bpId = bpId;
    this.environment = environment;
  }

  @When("creating intent template")
  public void createIntentTemplate() {
    try {
      this.mockTenantContext();
      IntentTemplate intentTemplate = this.inputIntentTemplateModel(INTENT_TEMPLATE_REQUEST_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/intent-template").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(intentTemplate))
              .contentType(MediaType.APPLICATION_JSON));
      this.intentId = intentTemplate.getIntentId();
      this.template = intentTemplate.getTemplate();
    } catch (Exception e) {
      log.error("Error while creating intent template", e);
    }
  }

  @When("getting intent template with intentId {string}")
  public void getIntentTemplate(String intentId) {
    try {
      this.mockTenantContext();
      resultActions = this.mockMvc.perform(get("/v1/intent-template").param("intentId", intentId)
          .contentType(MediaType.APPLICATION_JSON));
      log.info("Intent Template : {}",
          resultActions.andReturn().getResponse().getContentAsString());
    } catch (Exception e) {
      log.error("getting intent template failed: ", e);
    }
  }

  @When("updating intent template")
  public void updatingIntentTemplate() {
    try {
      this.mockTenantContext();
      IntentTemplate intentTemplate =
          this.inputIntentTemplateModel(INTENT_TEMPLATE_REQUEST_UPDATE_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/intent-template").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(intentTemplate))
              .contentType(MediaType.APPLICATION_JSON));
      this.template = intentTemplate.getTemplate();
      this.intentId = intentTemplate.getIntentId();
    } catch (Exception e) {
      log.error("update intent template failed: ", e);
    }
  }
  public void mockTenantContext() {
    TenantContext.setTenantContext(tenantId, environment);
  }

  @When("deleting intent template")
  public void deletingIntentTemplate() {
    try {
      this.mockTenantContext();
      IntentTemplate intentTemplate =
          this.inputIntentTemplateModel(INTENT_TEMPLATE_REQUEST_DELETE_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/intent-template").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(intentTemplate))
              .contentType(MediaType.APPLICATION_JSON));
      this.template = intentTemplate.getTemplate();
      this.intentId = intentTemplate.getIntentId();
    } catch (Exception e) {
      log.error("delete summary header template failed: ", e);
    }
  }

  @Then("the response is {int}")
  public void responseIs(int response) {
    Assert.assertEquals(response, resultActions.andReturn().getResponse().getStatus());
  }

  @Then("intent template should be created")
  public void intentTemplateCreated() {
    Optional<IntentTemplateDoc> createdIntentTemplate =
        this.intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment(this.intentId,
            this.tenantId, this.environment);
    log.info("Created Intent Template : {}", createdIntentTemplate);
    Assert.assertEquals(this.template, createdIntentTemplate.get().getTemplate());
  }

  @Then(
      "response contains intent template {string} and business process {string} and environment {string} and tenant id {string}")
  public void responseContainsIntentTemplateDetails(String template, String bpId,
      String environment, String tenantId) throws UnsupportedEncodingException {
    String response = resultActions.andReturn().getResponse().getContentAsString();
    log.info("Response : {}", response);
    Assert.assertTrue(response.contains(template));
    Assert.assertTrue(response.contains(bpId));
    Assert.assertTrue(response.contains(environment));
    Assert.assertTrue(response.contains(tenantId));
  }

  @Then("intent template should be updated")
  public void intentTemplateUpdated() {
    Optional<IntentTemplateDoc> updatedIntentTemplate =
        this.intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment(this.intentId,
            this.tenantId, this.environment);
    Assert.assertEquals(this.template, updatedIntentTemplate.get().getTemplate());
  }

  @Then("intent template is deleted")
  public void intentTemplateIsDeleted() {
    Optional<IntentTemplateDoc> deletedIntentTemplate =
        this.intentTemplateRepo.findIntentTemplateByIntentIdAndTenantIdAndEnvironment(this.intentId,
            this.tenantId, this.environment);
    Assert.assertEquals(this.template, deletedIntentTemplate.get().getTemplate());
  }

  @Then("intent template is set in cache")
  public void intentTemplateInCache() {
    String intentTemplate = this.intentTemplateCacheHelper.getIntentTemplate(this.intentId);
    String actualHeaderTemplate =
        "<p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>customer name</span></p>";
    Assert.assertEquals(actualHeaderTemplate, intentTemplate);
  }

  @Then("intent template is updated in cache")
  public void intentTemplateUpdatedInCache() {
    String intentTemplate = this.intentTemplateCacheHelper.getIntentTemplate(this.intentId);
    String actualHeaderTemplate =
        "<p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>John Doe</span></p>";
    Assert.assertEquals(actualHeaderTemplate, intentTemplate);
  }

  @Then("intent template is deleted in cache")
  public void intentTemplateIsDeletedInCache() {
    String intentTemplate = this.intentTemplateCacheHelper.getIntentTemplate(this.intentId);
    String actualHeaderTemplate = "";
    Assert.assertEquals(actualHeaderTemplate, intentTemplate);
  }

  private IntentTemplate inputIntentTemplateModel(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<IntentTemplate>() {
        });
  }
}
