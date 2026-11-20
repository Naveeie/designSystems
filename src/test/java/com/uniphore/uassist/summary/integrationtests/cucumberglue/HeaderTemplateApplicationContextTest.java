package com.uniphore.uassist.summary.integrationtests.cucumberglue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.cache.HeaderTemplateCacheHelper;
import com.uniphore.uassist.summary.dao.HeaderTemplateRepository;
import com.uniphore.uassist.summary.dao.documents.HeaderTemplateDoc;
import com.uniphore.uassist.summary.model.HeaderTemplate;
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
public class HeaderTemplateApplicationContextTest {

  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";
  private static final String HEADER_TEMPLATE_REQUEST_JSON = "header-template-request.json";
  private static final String HEADER_TEMPLATE_REQUEST_UPDATE_JSON =
      "header-template-request-update.json";
  private static final String HEADER_TEMPLATE_REQUEST_DELETE_JSON =
      "header-template-request-delete.json";
  private static final String USERNAME = "test-user";

  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private HeaderTemplateRepository headerTemplateRepo;

  @Autowired
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ResultActions resultActions;

  private String tenantId;
  private String experience;
  private String bpId;
  private String environment;
  private String template;
  private String userId;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
  }

  @Given(
      "tenant id {string} and experience {string} and business process {string} and environment {string}")
  public void givenTenantIdAndExpAndBpAndEnv(String tenantId, String experience, String bpId,
      String environment) throws InterruptedException {
    Thread.sleep(10000);
    this.tenantId = tenantId;
    this.experience = experience;
    this.bpId = bpId;
    this.environment = environment;
  }

  @When("creating summary header template")
  public void createSummaryHeaderTemplate() {
    try {
      HeaderTemplate headerTemplate = this.inputHeaderTemplateModel(HEADER_TEMPLATE_REQUEST_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/header-template").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(headerTemplate))
              .contentType(MediaType.APPLICATION_JSON));
      this.template = headerTemplate.getTemplate();
      this.userId = headerTemplate.getUserId();
    } catch (Exception e) {
      log.error("create summary header template failed: ", e);
    }
  }

  @When("getting summary header template with business process {string}")
  public void getSummaryHeaderTemplate(String bpId) {
    try {
      resultActions = this.mockMvc.perform(
          get("/v1/header-template").param("businessProcessId", bpId)
              .contentType(MediaType.APPLICATION_JSON));
      log.info("Header Template : {}",
          resultActions.andReturn().getResponse().getContentAsString());
    } catch (Exception e) {
      log.error("getting summary header template failed: ", e);
    }
  }

  @When("updating summary header template")
  public void updatingSummaryHeaderTemplateWithUsername() {
    try {
      HeaderTemplate headerTemplate =
          this.inputHeaderTemplateModel(HEADER_TEMPLATE_REQUEST_UPDATE_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/header-template").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(headerTemplate))
              .contentType(MediaType.APPLICATION_JSON));
      this.template = headerTemplate.getTemplate();
    } catch (Exception e) {
      log.error("update summary header template failed: ", e);
    }
  }

  public void mockTenantContext() {
    TenantContext.setTenantContext(tenantId, environment);
  }
  @When("deleting summary header template")
  public void deletingSummaryHeaderTemplate() {
    try {
      this.mockTenantContext();
      HeaderTemplate headerTemplate =
          this.inputHeaderTemplateModel(HEADER_TEMPLATE_REQUEST_DELETE_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/header-template").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(headerTemplate))
              .contentType(MediaType.APPLICATION_JSON));
      this.template = headerTemplate.getTemplate();
    } catch (Exception e) {
      log.error("delete summary header template failed: ", e);
    }
  }

  @Then("response is {int}")
  public void responseIs(int response) {
    Assert.assertEquals(response, resultActions.andReturn().getResponse().getStatus());
  }

  @Then("header template should be created")
  public void headerTemplateCreated() {
    Optional<HeaderTemplateDoc> createdHeaderTemplate =
        this.headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
            this.bpId, this.tenantId, this.environment);
    Assert.assertEquals(this.template, createdHeaderTemplate.get().getTemplate());
  }

  @Then(
      "response contains header template {string} and business process {string} and environment {string} and tenant id {string}")
  public void responseContainsHeaderTemplateDetails(String template, String bpId,
      String environment, String tenantId) throws UnsupportedEncodingException {
    String response = resultActions.andReturn().getResponse().getContentAsString();
    log.info("Response : {}", response);
    Assert.assertTrue(response.contains(template));
    Assert.assertTrue(response.contains(bpId));
    Assert.assertTrue(response.contains(environment));
    Assert.assertTrue(response.contains(tenantId));
  }

  @Then("header template should be updated")
  public void headerTemplateUpdated() {
    Optional<HeaderTemplateDoc> updatedHeaderTemplate =
        this.headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
            this.bpId, this.tenantId, this.environment);
    Assert.assertEquals(this.template, updatedHeaderTemplate.get().getTemplate());
  }

  @Then("header template is deleted")
  public void headerTemplateIsDeleted() {
    Optional<HeaderTemplateDoc> deletedHeaderTemplate =
        this.headerTemplateRepo.findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
            this.bpId, this.tenantId, this.environment);
    log.info("Header Template deleted : {}", deletedHeaderTemplate);
    Assert.assertEquals(this.template, deletedHeaderTemplate.get().getTemplate());
  }

  @Then("header template is set in cache")
  public void headerTemplateInCache() {
    String headerTemplate = this.headerTemplateCacheHelper.getHeaderTemplate(this.bpId);
    String actualHeaderTemplate =
        "<p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>customer name</span></p>";
    Assert.assertEquals(actualHeaderTemplate, headerTemplate);
  }

  @Then("header template is updated in cache")
  public void headerTemplateUpdatedInCache() {
    String headerTemplate = this.headerTemplateCacheHelper.getHeaderTemplate(this.bpId);
    String actualHeaderTemplate =
        "<p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>John Doe</span></p>";
    Assert.assertEquals(actualHeaderTemplate, headerTemplate);
  }

  @Then("header template is deleted in cache")
  public void headerTemplateIsDeletedInCache() {
    String headerTemplate = this.headerTemplateCacheHelper.getHeaderTemplate(this.bpId);
    String actualHeaderTemplate = "";
    Assert.assertEquals(actualHeaderTemplate, headerTemplate);
  }

  private HeaderTemplate inputHeaderTemplateModel(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<HeaderTemplate>() {
        });
  }
}
