package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.service.MetricService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class MetricControllerTest extends ApplicationTest {

  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";
  private static final String SUMMARY_DOC_RESPONSE_JSON = "summary-doc.json";

  private static final String USERNAME = "dev";
  final ObjectMapper objectMapper = new ObjectMapper();

  @MockBean
  private SummaryDoc summaryDoc;

  @MockBean
  private MetricService metricService;

  private ResponseEntity<ByteArrayResource> response;

  @Container
  public static MongoDBContainer container = new MongoDBContainer(DockerImageName.parse("mongo:5"));

  static {
    container.start();
  }

  @DynamicPropertySource
  static void mongoDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", container::getReplicaSetUrl);
  }

  @BeforeAll
  void setupData() throws IOException {
    TenantContext.setTenantContext("3e37631c-78ac-4137-8415-72dcd90b19fd", USERNAME);
    summaryDoc = this.inputSummaryDocResponse(SUMMARY_DOC_RESPONSE_JSON);
  }

  @Test
  @Order(1)
  void testGetSummaryDetailedReport() {
    try {
      List<String> conversationIds = Arrays.asList("1", "2", "3");
      String requestParam = String.join(",", conversationIds);
      Mockito.when(this.metricService.getSummaryDetailedReport(ArgumentMatchers.any()))
          .thenReturn(response);
      final ResultActions action = this.mockMvc.perform(
          get("/v2/report-by-convid").param("conversation-ids", requestParam)
              .contentType(MediaType.APPLICATION_JSON));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Agent Header Edits", e);
    }
  }

  private SummaryDoc inputSummaryDocResponse(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<SummaryDoc>() {
        });
  }
}
