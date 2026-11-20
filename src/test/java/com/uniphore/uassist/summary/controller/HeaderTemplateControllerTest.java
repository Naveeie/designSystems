package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.model.HeaderTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.ResourceUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
class HeaderTemplateControllerTest extends ApplicationTest {

  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/";
  private static final String HEADER_TEMPLATE_REQUEST_JSON = "header-template-request.json";
  private static final String HEADER_TEMPLATE_REMOVE_VAR_REQUEST_JSON =
      "header-template-request-remove-var.json";
  private static final String HEADER_TEMPLATE_BAD_REQUEST_JSON = "header-template-bad-request.json";

  private static final String BP_ID = "EnsembleBP01";
  private static final String USERNAME = "test";

  final ObjectMapper objectMapper = new ObjectMapper();

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @Test
  @Order(1)
  void testSaveHeaderTemplate() {
    try {
      final ResultActions action = this.mockMvc.perform(
              put("/v1/header-template").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                      this.inputHeaderTemplateModel(HEADER_TEMPLATE_REQUEST_JSON))))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Header Template", e);
    }
  }

  @Test
  @Order(2)
  void testSaveHeaderTemplateRemoveVariable() {
    try {
      final ResultActions action = this.mockMvc.perform(
              put("/v1/header-template").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                      this.inputHeaderTemplateModel(HEADER_TEMPLATE_REMOVE_VAR_REQUEST_JSON))))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Header Template", e);
    }
  }

  @Test
  @Order(3)
  void testSaveHeaderTemplateClientError() {
    try {
      this.mockMvc.perform(
              put("/summary/v1/header-template").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                      this.inputHeaderTemplateModel(HEADER_TEMPLATE_BAD_REQUEST_JSON))))
          .andExpect(status().is4xxClientError());
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Intent Template", e);
    }
  }

  @Test
  @Order(4)
  void testSaveHeaderTemplateBadRequest() {
    try {
      this.mockMvc.perform(put("/v1/header-template").requestAttr("X-Username", USERNAME)
          .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Header Template", e);
    }
  }

  @Test
  @Order(5)
  void testFetchHeaderTemplate() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/header-template").param("businessProcessId", BP_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Header Template ", e);
    }
  }

  @Test
  @Order(6)
  void testFetchHeaderTemplates() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/header-templates").param("businessProcessIds", BP_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Header Template ", e);
    }
  }

  private HeaderTemplate inputHeaderTemplateModel(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<HeaderTemplate>() {
        });
  }

}
