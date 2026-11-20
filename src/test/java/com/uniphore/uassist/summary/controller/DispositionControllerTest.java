package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
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
public class DispositionControllerTest extends ApplicationTest {

  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/";
  private static final String DISPOSITION_REQUEST_JSON = "disposition-request.json";
  private static final String DISPOSITION_REQUEST_WRONG_FIELDS_JSON =
      "disposition-request-wrong-fields.json";

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
  void testSaveDisposition() {
    try {
      final ResultActions action = this.mockMvc.perform(
              put("/v1/disposition").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).content(
                      this.objectMapper.writeValueAsBytes(this.inputDisposition(DISPOSITION_REQUEST_JSON))))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_200);
    } catch (final Exception e) {
      log.error("exception occurred while saving the Disposition: ", e);
      Assertions.fail("exception occurred while saving the Disposition", e);
    }
  }

  @Test
  @Order(2)
  void testSaveDispositionWithWrongData() {
    try {
      Assertions.assertThrows(UnrecognizedPropertyException.class, () -> {
        this.mockMvc.perform(put("/v1/disposition").contentType(MediaType.APPLICATION_JSON)
                .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                    this.inputDisposition(DISPOSITION_REQUEST_WRONG_FIELDS_JSON))))
            .andExpect(status().isOk());
      });
    } catch (final Exception e) {
      log.error("exception occurred while saving the Disposition: ", e);
      Assertions.fail("exception occurred while saving the Disposition", e);
    }
  }

  @Test
  @Order(3)
  void testSaveDispositionWithWrongUrl() {
    try {
      ResultActions action = this.mockMvc.perform(
          put("/v1/disp").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                  this.inputDisposition(DISPOSITION_REQUEST_JSON))));
      assertEquals(404, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_404);
    } catch (final Exception e) {
      log.error("exception occurred while saving the Disposition: ", e);
      Assertions.fail("exception occurred while saving the Disposition", e);
    }
  }

  @Test
  @Order(4)
  void testGetDisposition() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/disposition").param("businessProcessId", BP_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_200);
    } catch (final Exception e) {
      log.error("exception occurred while fetching the Disposition: ", e);
      Assertions.fail("exception occurred while fetching the Disposition", e);
    }
  }

  @Test
  @Order(5)
  void testGetDispositionWrongUrl() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/disp").param("businessProcessId", BP_ID)
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action, "Response is not null");
      assertEquals(404, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_404);
    } catch (final Exception e) {
      log.error("exception occurred while fetching the Disposition: ", e);
      Assertions.fail("exception occurred while fetching the Disposition", e);
    }
  }

  @Test
  @Order(6)
  void testGetDispositions() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/disposition-levels").param("businessProcessIds", BP_ID)
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_200);
    } catch (final Exception e) {
      log.error("exception occurred while fetching the Disposition levels: ", e);
      Assertions.fail("exception occurred while fetching the Disposition levels", e);
    }
  }

  @Test
  @Order(7)
  void testGetDispositionsWrongUrl() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/disposition-").param("businessProcessIds", BP_ID)
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action, "Response is not null");
      assertEquals(404, action.andReturn().getResponse().getStatus(), TestConstant.STATUS_404);
    } catch (final Exception e) {
      log.error("exception occurred while fetching the Disposition levels: ", e);
      Assertions.fail("exception occurred while fetching the Disposition levels", e);
    }
  }


  private Disposition inputDisposition(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<Disposition>() {
        });
  }

}
