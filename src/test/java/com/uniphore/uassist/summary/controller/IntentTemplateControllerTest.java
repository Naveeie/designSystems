package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.model.IntentTemplate;
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
class IntentTemplateControllerTest extends ApplicationTest {

  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/";
  private static final String INTENT_TEMPLATE_REQUEST_JSON = "intent-template-request.json";
  private static final String INTENT_TEMPLATE_BAD_REQUEST_JSON = "intent-template-bad-request.json";
  private static final String INTENT_TEMPLATE_WITH_SAME_INTENT_JSON =
      "intent-template-with-same-intent.json";

  private static final String INTENT_ID = "1";
  private static final String VARIABLE_ID = "10";
  private static final String USERNAME = "Agent1";

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
  void testSaveIntentTemplate() {
    try {
      final ResultActions action = this.mockMvc.perform(
          put("/v1/intent-template").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                  this.inputIntentTemplate(INTENT_TEMPLATE_REQUEST_JSON)))).andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Intent Template", e);
    }
  }

  @Test
  @Order(2)
  void testSaveIntentTemplatewithSameIntent() {
    try {
      this.mockMvc.perform(put("/v1/intent-template").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                  this.inputIntentTemplate(INTENT_TEMPLATE_WITH_SAME_INTENT_JSON))))
          .andExpect(status().isOk());
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Intent Template", e);
    }
  }

  @Test
  @Order(3)
  void testSaveIntentTemplateBadRequest() {
    try {
      this.mockMvc.perform(put("/v1/intent-template").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                  this.inputIntentTemplate(INTENT_TEMPLATE_BAD_REQUEST_JSON))))
          .andExpect(status().isBadRequest());
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Intent Template", e);
    }
  }

  @Test
  @Order(4)
  void testGetIntentTemplate() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/intent-template").param("intentId", INTENT_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Intent Template ", e);
    }
  }

  @Test
  @Order(5)
  void testgetIntentTemplates() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/intent-templates").param("intentIds", INTENT_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Intent Template ", e);
    }
  }

  @Test
  @Order(6)
  void testGetIntentTemplateWithNo() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/intent-template").param("intentId", INTENT_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Intent Template ", e);
    }
  }

  @Test
  @Order(7)
  void testIsVariableUsedInSummary() {
    try {
      final ResultActions action = this.mockMvc.perform(
              get("/v1/validate/" + VARIABLE_ID).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while checking if variable is used in summary ", e);
    }
  }

  private IntentTemplate inputIntentTemplate(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<IntentTemplate>() {
        });
  }

}
