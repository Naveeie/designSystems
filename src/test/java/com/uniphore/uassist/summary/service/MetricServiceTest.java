package com.uniphore.uassist.summary.service;

import com.uniphore.conversations.v1.Entity;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.helper.MetricHelper;
import com.uniphore.uassist.summary.util.ReportResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
public class MetricServiceTest extends ApplicationTest {
  @Autowired
  private MetricService metricService;
  private SummaryDoc summaryDoc;
  @MockBean
  private MetricHelper metricHelper;
  @MockBean
  private SummaryRuntimeDao summaryRuntimeDao;
  @MockBean
  private CSVService csvService;
  private ByteArrayResource byteArrayResource;
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
  void getSummaryDetailedReport_withValidConversationIds_returnsZipResponse() throws IOException {
    List<String> convIds = Arrays.asList("Conv1", "Conv2");
    SummaryDoc summaryDoc = inputSummaryDoc();
    when(metricHelper.buildSummaryReport(anyString(), anyString())).thenReturn(
        new ReportResponse("Conv1", "Success", null));
    when(csvService.dataToCSVFile(anyString(), anyList())).thenReturn(String.valueOf(true));
    when(metricHelper.buildZipResponse(any(File.class), anyString())).thenReturn(
        ResponseEntity.ok(new ByteArrayResource(new byte[0])));

    ResponseEntity<ByteArrayResource> response = metricService.getSummaryDetailedReport(convIds);

    Assertions.assertNotNull(response);
    Assertions.assertEquals(200, response.getStatusCodeValue());
  }

  private HeaderSummaryDoc inputHeaderSummaryDoc() {
    return HeaderSummaryDoc.builder().plainText("plainText").template("<p>plainText</p>")
        .timestamp(TimeStampDoc.builder().seconds(1680202333).nanos(21314).build()).build();
  }

  private IntentSummaryDoc inputIntentSummaryDoc() {
    return IntentSummaryDoc.builder().intentId("12").instanceId("12").plainText("plaintext")
        .source("USER").template("template").disposition("disp").build();
  }

  private SummaryDoc inputSummaryDoc() {
    Map<String, IntentSummaryDoc> intentMap = new HashMap<>();
    intentMap.put("key1", inputIntentSummaryDoc());
    Map<String, IntentSummaryDoc> systemIntentSummaries = new HashMap<>();
    systemIntentSummaries.put("123", inputIntentSummaryDoc());
    Entity entity = Entity.newBuilder().setEntityId("entityId").setEntityInstanceId("instanceId")
        .setValue("entity1-1").setDetectedBy("USER").setEntityId("tenantId").build();
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    SummaryDoc.Slot slot =
        SummaryDoc.Slot.builder().id("slotId").instanceId("instanceId").value("values").build();
    entities.put("Slot1", slot);
    GenAiSummaryDoc.Section section =
        GenAiSummaryDoc.Section.builder().id("id").text("text").build();
    List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
    sectionList.add(section);
    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
    return SummaryDoc.builder().conversationId("ConvId").tenantId("tenantId").environment("dev")
        .businessProcessId("bp").language("en").experienceId("exp1")
        .headerSummary(inputHeaderSummaryDoc()).intentSummaries(intentMap).entities(entities)
        .variables(entities).agentNotes(null).participants(null)
        .timestamp(TimeStampDoc.builder().nanos(1234512).seconds(63531).build())
        .genAiSummary(genAiSummaryDoc).build();
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

}
