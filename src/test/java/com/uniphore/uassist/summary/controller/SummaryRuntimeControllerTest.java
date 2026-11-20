package com.uniphore.uassist.summary.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.featureflag.Tenant;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.ri.common.validation.OutputProcessor;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.SummaryRepository;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.helper.DataHelper;
import com.uniphore.uassist.summary.model.*;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.*;

import static com.uniphore.featureflag.LaunchDarklyConstants.FF_CONTEXT_KIND;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;
import static com.uniphore.uassist.summary.common.Constants.v1SUMMARY;
import static com.uniphore.uassist.summary.utils.TestConstant.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
public class SummaryRuntimeControllerTest extends ApplicationTest {

  @Value("${app.redis.summary-cache.ttl:3600000}")
  private long cachettl;

  final ObjectMapper objectMapper = new ObjectMapper();
  @MockBean
  private SummaryRepository summaryRepository;

  @Autowired
  private CacheOperations<TypeReference<Tenant>> tenantCache;

  @MockBean
  private LaunchDarklyHelper ldHelper;

  @MockBean
  private Logger logger;

  @MockBean
  private SummaryRuntimeService summaryRuntimeService;

  @MockBean
  private SummaryRuntimeDao summaryRuntimeDao;

  @MockBean
  private SummaryDoc summaryDoc;

  @MockBean
  private GenAISummaryCache genAISummaryCache;

  @MockBean
  private GenAISummaryConfig genAISummaryConfig;

  @MockBean
  private CacheOperations<Map<String, AgentStepPojo>> agentStepsCache;
  @Autowired
  private DataHelper dataHelper;

  @InjectMocks
  private SummaryRuntimeController summaryRuntimeController;

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
  void setupData() {
    try {
      when(logger.isDebugEnabled()).thenReturn(Boolean.valueOf("true"));
      TenantContext.setTenantContext("3e37631c-78ac-4137-8415-72dcd90b19fd", USERNAME);
      summaryDoc = this.dataHelper.inputSummaryDocResponse(SUMMARY_DOC_RESPONSE_JSON);
      genAISummaryCache = this.dataHelper.inputGenAISummaryResponse(GEN_AI_CACHE_JSON);
      genAISummaryConfig = this.dataHelper.inputGenAISummaryConfigResponse(GEN_AI_CONFIG_JSON);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  @Order(1)
  void testSaveAgentNotes() {
    try {
      AgentRequestPayload request =
          this.dataHelper.inputAgentNotesRequestPayload(AGENT_NOTES_REQUEST_JSON);
      when(this.summaryRuntimeService.saveAgentNotes(any())).thenReturn(
          new MessageResponse(Constants.SAVE, request.getConversationId()));
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent-notes").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME)
              .content(this.objectMapper.writeValueAsBytes(request))).andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while saving the Agent Notes", e);
    }
  }

  @Test
  void testSaveAgentNotesWithCustomException() {
    try {
      doThrow(new CustomException("Conversation not found hence not able to save agent notes",
          HttpStatus.BAD_REQUEST)).when(this.summaryRuntimeService).saveAgentNotes(any());
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent-notes").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                  this.dataHelper.inputAgentNotesRequestPayload(AGENT_NOTES_REQUEST_JSON))));

      assertNotNull(action, "Conversation not found hence not able to save agent notes");
      assertEquals(400, action.andReturn().getResponse().getStatus());
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while saving the Agent Notes", e);
    }
  }

  @Test
  @Order(2)
  void testSaveAgentNotesWithNull() {
    try {
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent-notes").contentType(MediaType.APPLICATION_JSON)
              .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                  this.dataHelper.inputAgentNotesRequestPayload(AGENT_NOTES_NO_VALUES_REQUEST_JSON))));
      assertNotNull(action, "Response is not null");
      assertEquals(400, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while saving the Agent Notes", e);
    }
  }

  @Test
  @Order(3)
  void testUpsertAgentIntentEdits() {
    try {
      super.mockTenantContext();
      when(this.summaryRepository.findById(any(String.class))).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              put("/v1/agent-edits").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).requestAttr("X-Tenant-Id", TENANT_ID).content(
                      this.objectMapper.writeValueAsBytes(
                          this.dataHelper.inputAgentNotesRequestPayload(AGENT_INTENT_EDITS_REQUEST_JSON))))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Agent Intent Edits", e);
    }
  }

  @Test
  @Order(4)
  void testUpsertAgentHeaderEdits() {
    try {
      super.mockTenantContext();
      when(this.summaryRepository.findById(any(String.class))).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);

      final ResultActions action = this.mockMvc.perform(
              put("/v1/agent-edits").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                      this.dataHelper.inputAgentNotesRequestPayload(AGENT_HEADER_EDITS_REQUEST_JSON))))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Agent Header Edits", e);
    }
  }

  @Test
  @Order(5)
  void testSaveAddIntent() {
    try {
      super.mockTenantContext();
      when(this.summaryRepository.findById(any(String.class))).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);

      final ResultActions action = this.mockMvc.perform(
              post("/v1/agent/intent").contentType(MediaType.APPLICATION_JSON)
                  .requestAttr("X-Username", USERNAME).content(this.objectMapper.writeValueAsBytes(
                      this.dataHelper.inputAgentNotesRequestPayload(INTENT_ADD_REQUEST_JSON))))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Add intent", e);
    }
  }

  @Test
  @Order(6)
  void testDeleteIntent() {
    try {
      super.mockTenantContext();
      when(this.summaryRepository.findById(any(String.class))).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);

      final ResultActions action = this.mockMvc.perform(
          delete("/v1/agent/intent/{intentId}", "1").param("conversationId", "cfr3sh3hjl1dk9u130hl")
              .requestAttr("X-Username", USERNAME)).andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while deleting intent", e);
    }
  }

  @Test
  void testSummaryReviewed_emptyConversationId() {
    ResponseEntity<Object> response =
        summaryRuntimeController.summaryReviewed("", Optional.of(Boolean.FALSE),Optional.of(Boolean.FALSE),
            Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Conversation ID is null/empty/undefined", response.getBody());
  }

  @Test
  void testSummaryReviewed_nullConversationId() {
    ResponseEntity<Object> response =
        summaryRuntimeController.summaryReviewed("null", Optional.of(Boolean.FALSE),Optional.of(Boolean.FALSE),
            Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Conversation ID is null/empty/undefined", response.getBody());
  }

  @Test
  void testSummaryReviewed_undefinedConversationId() {
    ResponseEntity<Object> response =
        summaryRuntimeController.summaryReviewed("undefined", Optional.of(Boolean.FALSE),Optional.of(Boolean.FALSE),
            Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Conversation ID is null/empty/undefined", response.getBody());
  }

  @Test
  void testSummaryReviewed_ValidConvIdButNoSummaryDocFound() {
    String convId = "cfr3sh3hjl1dk9u130hl";
    Optional<Boolean> autoSubmit = Optional.of(Boolean.TRUE);
    Mockito.when(this.summaryRuntimeService.summaryReviewed(convId, autoSubmit, Optional.of(false),Optional.of(AutoSubmitOption.AUTO_SUBMIT))).thenReturn(
        new MessageResponse("No summary was found in cache or database", "cfr3sh3hjl1dk9u130hl"));
    ResponseEntity<Object> response = summaryRuntimeController.summaryReviewed(convId, autoSubmit,
        Optional.of(false),Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    MessageResponse messageResponse = (MessageResponse) response.getBody();
    assertEquals("No summary was found in cache or database", messageResponse.getMessage());
    assertEquals("cfr3sh3hjl1dk9u130hl", messageResponse.getId());
  }

  @Test
  void testSummaryReviewed_ValidConvIdButExceptionOccurred() {
    String convId = "cfr3sh3hjl1dk9u130hl";
    Optional<Boolean> autoSubmit = Optional.of(Boolean.TRUE);
    when(this.summaryRuntimeService.summaryReviewed(convId, autoSubmit,Optional.of(Boolean.FALSE),Optional.of(AutoSubmitOption.AUTO_SUBMIT))).thenThrow(
        new RuntimeException("exception occurred while reviewing summary"));
    ResponseEntity<Object> response = summaryRuntimeController.summaryReviewed(convId, autoSubmit,Optional.of(Boolean.FALSE)
    ,Optional.of(AutoSubmitOption.AUTO_SUBMIT));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("exception occurred while reviewing summary", response.getBody());
  }

  @Test
  @Order(7)
  void testSaveSummaryReviewed() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeService.summaryReviewed(any(), any(),any(),any())).thenReturn(
          new MessageResponse(Constants.SUCCESS, "cfr3sh3hjl1dk9u130hl"));
      when(this.summaryRepository.findById(any(String.class))).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
          LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);

      final ResultActions action = this.mockMvc.perform(
              post("/v1/agent/summary-reviewed").param("conversationId", "cfr3sh3hjl1dk9u130hl")
                  .contentType(MediaType.APPLICATION_JSON).requestAttr("X-Username", USERNAME))
          .andExpect(status().isOk());
      assertNotNull(action, "Response is not null");
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Summary submit", e);
    }
  }

  @Test
  @Order(19)
  void testSaveSummaryReviewedFailed() {
    try {
      super.mockTenantContext();
      doThrow(new CustomException("", HttpStatus.NO_CONTENT)).when(this.summaryRuntimeService)
          .summaryReviewed(any(), any(),any(),any());

      final ResultActions action = this.mockMvc.perform(
              post("/v1/agent/summary-reviewed").param("conversationId", "cfr3sh3hjl1dk9u130hl")
                  .contentType(MediaType.APPLICATION_JSON).requestAttr("X-Username", USERNAME))
          .andExpect(status().is4xxClientError());
      assertNotNull(action, "Response is not null");
      assertEquals(404, action.andReturn().getResponse().getStatus(), STATUS_404);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while saving the Summary submit", e);
    }
  }

  @Test
  @Order(8)
  void testFetchSummaries() {
    try {
      super.mockTenantContext();
      when(this.summaryRepository.findById(any(String.class))).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SYSTEM_TEMPLATE_SUMMARY_FLAG,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/summaries").param("conversationId", "cfr3sh3hjl1dk9u130hl")
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Summaries ", e);
    }
  }

  @Test
  @Order(9)
  void testFetchSummaryEventLog() {
    super.mockTenantContext();
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/event-logs").param("conversationId", "cfr3sh3hjl1dk9u130hl")
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Summary event log  ", e);
    }
  }

  @Test
  @Order(10)
  void testFetchGenAiSummary() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeService.createGenAiSummary(any(), any(), any(), anyBoolean(),
          any())).thenReturn(summaryDoc);
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EVENT_LOG_ENABLES,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/gen-ai-summaries").param("conversation_id", "cfr3sh3hjl1dk9u130hl")
              .param("experience_id", "9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5")
              .param("session_id", "cfr3sh3hjl1dk9u130hlg2we")
              .contentType(MediaType.APPLICATION_JSON));
    } catch (final Exception e) {
      log.error("Exception: ", e);
      Assertions.fail("Exception occurred while fetching Gen AI Summary", e);
    }
  }

  @Test
  @Order(11)
  void testFetchGenAiSummaryWithNullValues() {
    try {
      when(this.summaryRuntimeService.createGenAiSummary(any(), any(), any(), anyBoolean(),
          any())).thenReturn(this.dataHelper.inputSummaryDoc());
      final ResultActions action = this.mockMvc.perform(
          get("/v1/gen-ai-summaries").param("conversation_id", "cfr3sh3hjl1dk9u130hl")
              .param("experience_id", "9e9d1fcc-f99d-400c-bfb8-95d79a6ba9e5")
              .param("session_id", "session1").contentType(MediaType.APPLICATION_JSON));
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("Exception: ", e);
      Assertions.fail("Exception occurred while fetching Gen AI Summary", e);
    }
  }

  @Test
  @Order(12)
  void testFetchGenAiSummaryWithNullExp() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/gen-ai-summaries").param("conversation_id", "").param("experience_id", "")
              .param("session_id", "").contentType(MediaType.APPLICATION_JSON));
      assertEquals(400, action.andReturn().getResponse().getStatus(), STATUS_400);
    } catch (final Exception e) {
      log.error("Exception: ", e);
      Assertions.fail("Exception occurred while fetching Gen AI Summary", e);
    }
  }

  @Test
  @Order(13)
  void testFetchProcessMetrics() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/event-metrics").param("conversationId", "cfr3sh3hjl1dk9u130hl")
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching process metrics ", e);
    }
  }

  @Test
  @Order(14)
  void testFetchSummaryOutline() {
    try {
      final ResultActions action = this.mockMvc.perform(
          get("/v1/summary-report").param("conversationId", "cfr3sh3hjl1dk9u130hl")
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while fetching Summary outline ", e);
    }
  }

  @Test
  @Order(15)
  void testIntentReviewed() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeDao.getSummaryDoc(any())).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      IntentSummaryReviewedRequest.IntentSummary intentSummary =
          IntentSummaryReviewedRequest.IntentSummary.builder().intentId("1").instanceId("10")
              .template("<p>abc</p>").plainText("abc").disposition("2").build();
      List<IntentSummaryReviewedRequest.IntentSummary> intentSummaries = new ArrayList<>();
      intentSummaries.add(intentSummary);
      IntentSummaryReviewedRequest intentSummaryReviewedRequest =
          new IntentSummaryReviewedRequest();
      intentSummaryReviewedRequest.setIntentSummaries(intentSummaries);
      String jsonRequest = new ObjectMapper().writeValueAsString(intentSummaryReviewedRequest);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent/intents-reviewed/" + conversationId).param("intentSummaryReviewedRequest",
              "intent").content(jsonRequest).contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while intent review ", e);
    }
  }

  @Test
  @Order(16)
  void testIntentReviewedWithError() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeDao.getSummaryDoc(any())).thenReturn(Optional.of(summaryDoc));
      tenantCache.put(TENANT_CACHE_KEY, TENANT_ID, new TypeReference<Tenant>() {
      });
      IntentSummaryReviewedRequest.IntentSummary intentSummary =
          IntentSummaryReviewedRequest.IntentSummary.builder().intentId("1").instanceId("10")
              .template("<p>abc</p>").plainText("abc").disposition("2").build();
      List<IntentSummaryReviewedRequest.IntentSummary> intentSummaries = new ArrayList<>();
      intentSummaries.add(intentSummary);
      IntentSummaryReviewedRequest intentSummaryReviewedRequest =
          new IntentSummaryReviewedRequest();
      intentSummaryReviewedRequest.setIntentSummaries(intentSummaries);
      String jsonRequest = new ObjectMapper().writeValueAsString(intentSummaryReviewedRequest);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      when(this.summaryRuntimeService.submitReviewedIntentSummaries(conversationId,
          intentSummaryReviewedRequest)).thenThrow(new RuntimeException("Exception"));
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent/intents-reviewed/" + conversationId).param("intentSummaryReviewedRequest",
              "intent").content(jsonRequest).contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(500, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while intent review ", e);
    }
  }

  @Test
  @Order(17)
  void testAddAgentStepManual() {
    try {
      super.mockTenantContext();
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      final ResultActions action = this.mockMvc.perform(post(
          "/v1/agent/agent-steps/" + conversationId + "?text=test&environment=dev").contentType(
          MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while adding agent step manually ", e);
    }
  }

  @Test
  @Order(18)
  void testDeleteAgentStepManual() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeDao.getSummaryDoc(any(String.class))).thenReturn(
          Optional.of(summaryDoc));
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      Map<String, AgentStepPojo> cache = new HashMap<>();
      when(this.summaryRuntimeService.deleteAgentStep(any(), any())).thenReturn(true);
      this.agentStepsCache.put("1", "1", cache, this.cachettl);
      final ResultActions action = this.mockMvc.perform(
          delete("/v1/agent/agent-steps/" + conversationId).param("id", "1")
              .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while deleting agent step manually ", e);
    }
  }

  @Test
  @Order(19)
  void testDeleteAgentStepManualWithError() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeDao.getSummaryDoc(any(String.class))).thenReturn(
          Optional.of(summaryDoc));
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      when(logger.isDebugEnabled()).thenReturn(true);
      Map<String, AgentStepPojo> cache = new HashMap<>();
      when(this.summaryRuntimeService.deleteAgentStep(any(), any())).thenReturn(false);
      this.agentStepsCache.put("1", "1", cache, this.cachettl);
      final ResultActions action = this.mockMvc.perform(
          delete("/v1/agent/agent-steps/" + conversationId).param("id", "1")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(400, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while deleting agent step manually ", e);
    }
  }

  @Test
  @Order(20)
  void testEditAgentStepsManual() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeService.updateAgentStep(any(), any(), any())).thenReturn(true);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      Map<String, AgentStepPojo> cache = new HashMap<>();
      this.agentStepsCache.put("1", "1", cache, this.cachettl);
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent/agent-steps/" + conversationId + "?id=1&text=abc").contentType(
              MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while editing agent step manually ", e);
    }
  }

  @Test
  @Order(21)
  void testEditAgentStepsManualErrorState() {
    try {
      super.mockTenantContext();
      when(this.summaryRuntimeService.updateAgentStep(any(), any(), any())).thenReturn(false);
      doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);
      String conversationId = "cfr3sh3hjl1dk9u130hl";
      Map<String, AgentStepPojo> cache = new HashMap<>();
      this.agentStepsCache.put("1", "1", cache, this.cachettl);
      final ResultActions action = this.mockMvc.perform(
          put("/v1/agent/agent-steps/" + conversationId + "?id=1&text=abc").contentType(
              MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(400, action.andReturn().getResponse().getStatus(), STATUS_400);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while deleting agent step manually ", e);
    }
  }

  @Test
  @Order(22)
  void testGetSummariesBySessionIdWithBadRequest() {
    try {
      super.mockTenantContext();
      when(logger.isDebugEnabled()).thenReturn(true);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/summary").param("sessionIds", "1", "2", "3")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(400, action.andReturn().getResponse().getStatus(), STATUS_400);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while deleting agent step manually ", e);
    }

  }

  @Test
  @Order(23)
  void testGetSummariesBySessionIdWith() {
    try {
      super.mockTenantContext();
      List<SummaryResponse> summaryResponses = new ArrayList<>();
      summaryResponses.add(this.dataHelper.summaryResponseBuilder());
      when(this.summaryRuntimeService.fetchSummaryBySessionIds(any())).thenReturn(summaryResponses);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/summary").param("sessionIds", "1", "2", "3")
              .contentType(MediaType.APPLICATION_JSON));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while deleting agent step manually ", e);
    }
  }

  @Test
  void testFetchGenAiSummary_shouldReturnEmptySummary_whenGenAiSummaryDocIsNull() throws Exception {
    when(summaryRuntimeService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        any())).thenReturn(null);

    mockMvc.perform(get("/v1/gen-ai-summaries").param("conversation_id", "testConvId")
            .param("experience_id", "testExpId").param("format_enable", "false"))
        .andExpect(status().isOk());

    verify(summaryRuntimeService).createGenAiSummary(eq("testConvId"), anyString(),
        eq(Optional.of("testExpId")), eq(false), any());
  }

  @Test
  void testFetchGenAiSummary_shouldThrowError_whenAllParametersAreMissing() throws Exception {
    mockMvc.perform(get("/v1/gen-ai-summaries")).andExpect(status().isBadRequest());

    verify(summaryRuntimeService, never()).createGenAiSummary(anyString(), anyString(), any(),
        anyBoolean(), any());
  }

  @Test
  void testFetchGenAiSummary_shouldConvertSessionIdToConvAndExpId() throws Exception {
    String[] convAndExpIds = {"testConvId", "testExpId"};
    when(summaryRuntimeService.getConvIdAndExpIdByExtId("testSessionId")).thenReturn(convAndExpIds);

    when(summaryRuntimeService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        any())).thenReturn(null);

    mockMvc.perform(get("/v1/gen-ai-summaries").param("session_id", "testSessionId"))
        .andExpect(status().isOk());

    verify(summaryRuntimeService).getConvIdAndExpIdByExtId("testSessionId");
    verify(summaryRuntimeService).createGenAiSummary(eq("testConvId"), anyString(),
        eq(Optional.of("testExpId")), eq(false), any());
  }

  @Test
  void testFetchGenAiSummary_shouldThrowError_whenSessionIdIsInvalid() throws Exception {
    when(summaryRuntimeService.getConvIdAndExpIdByExtId("invalidSessionId")).thenReturn(null);

    mockMvc.perform(get("/v1/gen-ai-summaries").param("session_id", "invalidSessionId"))
        .andExpect(status().isBadRequest());

    verify(summaryRuntimeService).getConvIdAndExpIdByExtId("invalidSessionId");
  }

  @Test
  void testFetchGenAiSummary_shouldThrowError_whenConversationIdIsMissing() throws Exception {
    mockMvc.perform(get("/v1/gen-ai-summaries").param("experience_id", "testExpId"))
        .andExpect(status().isBadRequest());

    verify(summaryRuntimeService, never()).createGenAiSummary(anyString(), anyString(), any(),
        anyBoolean(), any());
  }

  @Test
  void testFetchGenAiSummary_shouldThrowError_whenExperienceIdIsMissing() throws Exception {
    mockMvc.perform(get("/v1/gen-ai-summaries").param("conversation_id", "testConvId"))
        .andExpect(status().isBadRequest());

    verify(summaryRuntimeService, never()).createGenAiSummary(anyString(), anyString(), any(),
        anyBoolean(), any());
  }

  @Test
  void testFetchGenAiSummary_shouldHandleConvAndExpIdsPresentWithoutSessionId() throws Exception {
    when(summaryRuntimeService.createGenAiSummary(anyString(), anyString(), any(), anyBoolean(),
        any())).thenReturn(null);

    mockMvc.perform(get("/v1/gen-ai-summaries").param("conversation_id", "testConvId")
        .param("experience_id", "testExpId")).andExpect(status().isOk());

    verify(summaryRuntimeService).createGenAiSummary(eq("testConvId"), anyString(),
        eq(Optional.of("testExpId")), eq(false), any());
  }

  @Test
  void testFetchGenAiSummary_shouldThrowError_whenSessionIdConversionFails() throws Exception {
    when(summaryRuntimeService.getConvIdAndExpIdByExtId("invalidSessionId")).thenReturn(
        new String[] {});

    mockMvc.perform(get("/v1/gen-ai-summaries").param("session_id", "invalidSessionId"))
        .andExpect(status().isBadRequest());

    verify(summaryRuntimeService).getConvIdAndExpIdByExtId("invalidSessionId");
  }

  @Test
  void testFetchSummary_success() throws Exception {
    SummaryResponse response = new SummaryResponse();
    when(summaryRuntimeService.fetchSummary("123")).thenReturn(response);

    mockMvc.perform(
            get(v1SUMMARY).param("conversationId", "123").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturnNoContent_whenCustomExceptionThrown() throws Exception {
    when(summaryRuntimeService.fetchSummary("123")).thenThrow(
        new CustomException("NO_SUMMARY", HttpStatus.NO_CONTENT));

    mockMvc.perform(
            get(v1SUMMARY).param("conversationId", "123").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldReturnInternalServerError_whenRuntimeExceptionThrown() throws Exception {
    when(summaryRuntimeService.fetchSummary("123")).thenThrow(
        new RuntimeException("Internal error"));

    mockMvc.perform(
            get(v1SUMMARY).param("conversationId", "123").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError()).andExpect(content().string("Internal error"));
  }

  @Test
  void shouldReturnBadRequest_whenConversationIdIsEmpty() throws Exception {
    mockMvc.perform(
            get(v1SUMMARY).param("conversationId", "").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @AfterAll
  static void stopContainer() {
    container.stop();
  }

  @Test
  void test_UpdateSummaryTemplate_When_TemplateBasedGenAISummaryFF_IsDisabled() {
    SummaryTemplate request = SummaryTemplate.builder().build();

    Mockito.doReturn(false).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.failureUIResponse(HttpStatus.FORBIDDEN,
            "NG template based GEN-AI summary generation feature is disabled");

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeController.updateSummaryTemplate("67767f93-a9c8-4b4b-a85f-0f793da2f763",
            request);
    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(Objects.requireNonNull(expectedResponse.getBody()).get("message"),
        Objects.requireNonNull(actualResponse.getBody()).get("message"));
  }

  @Test
  void test_Successful_UpdateSummaryTemplate() {
    SummaryTemplate request =
        SummaryTemplate.builder().conversationId("67767f93-a9c8-4b4b-a85f-0f793da2f763")
            .summaryTemplateId("67767f93-a9c8-4b4b-a85f-0f793da2f763").version(1).build();

    Mockito.doReturn(true).when(ldHelper)
        .isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);

    ResponseEntity<Map<String, Object>> expectedResponse =
        OutputProcessor.successUIResponse(HttpStatus.OK, null);
    Mockito.when(this.summaryRuntimeService.updateSummaryTemplate(request))
        .thenReturn(expectedResponse);

    ResponseEntity<Map<String, Object>> actualResponse =
        this.summaryRuntimeController.updateSummaryTemplate("67767f93-a9c8-4b4b-a85f-0f793da2f763",
            request);
    assertEquals(expectedResponse.getStatusCode(), actualResponse.getStatusCode());
    assertEquals(expectedResponse.getBody(), actualResponse.getBody());
  }

  @Test
  void testSummaryGenerated_PositiveFlow() {
    String convId = "cfr3sh3hjl1dk9u130hl";
    String templateId="dummy";
    int version=1;
    when(this.summaryRuntimeService.createGenAiSummaryIndependent(convId,templateId,version)).thenReturn(
        GenAiSummaryDoc.builder().build());
    ResponseEntity<Map<String, GenAiSummaryDoc>>
        response = summaryRuntimeController.GenerateSummary(convId, templateId,version);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void testSummaryGenerated_PositiveFlow_DocNull() {
    String convId = "cfr3sh3hjl1dk9u130hl";
    String templateId="dummy";
    int version=1;
    when(this.summaryRuntimeService.createGenAiSummaryIndependent(convId,templateId,version)).thenReturn(null);
    ResponseEntity<Map<String, GenAiSummaryDoc>>
        response = summaryRuntimeController.GenerateSummary(convId, templateId,version);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void testSummaryGenerated_PositiveFlow_ErrorThrow() {
    String convId = "cfr3sh3hjl1dk9u130hl";
    String templateId="dummy";
    int version=1;
    when(this.summaryRuntimeService.createGenAiSummaryIndependent(convId,templateId,version)).thenThrow(new RuntimeException());
    ResponseEntity<Map<String, GenAiSummaryDoc>>
        response = summaryRuntimeController.GenerateSummary(convId, templateId,version);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void testFetchSummaries_Success() {
    try {
      List<String> request =Arrays.asList("conv1", "conv2", "conv3");

      SummaryResponse summaryResponse1 = new SummaryResponse();
      SummaryResponse summaryResponse2 = new SummaryResponse();
      SummaryResponse summaryResponse3 = new SummaryResponse();
      List<SummaryResponse> summaryResponses = Arrays.asList(summaryResponse1, summaryResponse2, summaryResponse3);

      when(this.summaryRuntimeService.fetchSummaries(Arrays.asList("conv1", "conv2", "conv3")))
          .thenReturn(summaryResponses);

      final ResultActions action = this.mockMvc.perform(
          post("/v1/summaries-list").param("environment", "dev")
              .contentType(MediaType.APPLICATION_JSON)
              .content(this.objectMapper.writeValueAsBytes(request)))
          .andExpect(status().isOk());

      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), STATUS_200);
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while fetching summaries list", e);
    }
  }

  @Test
  void testFetchSummaries_WithCustomException() {
    try {
      List<String> convIds=Arrays.asList("conv1");

      when(this.summaryRuntimeService.fetchSummaries(Arrays.asList("conv1")))
          .thenThrow(new CustomException("Summary not found", HttpStatus.NOT_FOUND));

      final ResultActions action = this.mockMvc.perform(
          post("/v1/summaries-list").param("environment", "dev")
              .contentType(MediaType.APPLICATION_JSON)
              .content(this.objectMapper.writeValueAsBytes(convIds)))
          .andExpect(status().isNoContent());

      assertEquals(204, action.andReturn().getResponse().getStatus());
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while testing CustomException scenario", e);
    }
  }

}
