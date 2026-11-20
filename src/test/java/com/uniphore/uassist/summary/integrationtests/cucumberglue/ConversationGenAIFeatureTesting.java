package com.uniphore.uassist.summary.integrationtests.cucumberglue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.Timestamps;
import com.uniphore.conversations.v1.*;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.llminference.v1.Section;
import com.uniphore.llminference.v1.*;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.cache.*;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.HeaderSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.integrationtests.IntegrationTestHelper;
import com.uniphore.uassist.summary.kafka.MessageConsumer;
import com.uniphore.uassist.summary.model.GenAISummaryConfig;
import com.uniphore.uassist.summary.model.MessageResponse;
import com.uniphore.uassist.summary.util.GrpcClientHelper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentMatchers;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.UASSIST_STATE_V2_UPDATE_FF;
import static com.uniphore.uassist.summary.kafka.MessageConsumer.UASSIST_PROCESS_CONVERSATION_EVENTS;
import static com.uniphore.uassist.summary.utils.TestConstant.EXPECTED_PLAINTEXT;
import static com.uniphore.uassist.summary.utils.TestConstant.EXPECTED_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Slf4j
public class ConversationGenAIFeatureTesting {

  private static final String GEN_AI_CONFIG_JSON = "gen-ai-config.json";
  private static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";
  private static final String USERNAME = "dev";

  public static final String END_OF_SECTION = "end of section";
  public static final String TEXT = "text";
  public static final String ID_123 = "id123";
  public static final String VAR_123 = "var123";
  public static final String NAME_1 = "name_1";
  public static final String TYPE = "type";
  public static final String EN = "en";

  @Autowired
  private CustomMetricsPublisher customMetricsPublisher;
  private MockMvc mockMvc;
  @Autowired
  private CacheRefreshHelper cacheRefreshHelper;
  @Autowired
  private MessageConsumer messageConsumer;
  @Autowired
  private SummaryRuntimeDao summaryRuntimeDao;
  @Autowired
  private LaunchDarklyHelper ldHelper;
  @MockBean
  @Qualifier("headerTemplateLocalCache")
  private HashLocalCache<String> headerTemplateLocalCache;
  @Autowired
  private IntentTemplateCacheHelper intentTemplateCacheHelper;
  @Autowired
  private HeaderTemplateCacheHelper headerTemplateCacheHelper;
  @Autowired
  private IntegrationTestHelper integrationTestHelper;
  @Autowired
  private GrpcClientHelper grpcClientHelper;

  private String conversationId;
  private String externalConversationId;
  private String tenantId;
  private String environment;
  private String bpId;
  private String expId;
  private String language;
  private int order;
  private String participantType;
  @Autowired
  private WebApplicationContext webApplicationContext;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Common setup before each scenario
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
  }

  @Given(
      "For Gen-ai Conversation id is {string} ExternalConversation id is {string} Tenant id is {string} Environment is {string} BusinessProcess id is {string} and Experience id is {string}")
  public void givenConversationDetails(String conv_id, String extConvId, String tenant_Id,
      String Environment, String bpId, String expId) {
    conversationId = conv_id;
    externalConversationId = extConvId;
    tenantId = tenant_Id;
    environment = Environment;
    this.bpId = bpId;
    this.expId = expId;
    createGenAiConfiguration();
  }

  @Given("Gen AI : Language is {string} ParticipantType is {string} and Order is {int}")
  public void flowDetails(String Language, String ParticipantType, int Order) {
    this.language = Language;
    this.participantType = ParticipantType;
    this.order = Order;
  }

  private GenAISummaryConfig inputGenAISummaryConfigResponse(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfig>() {
        });
  }

  public void createGenAiConfiguration() {
    try {
      this.mockTenantContext();
      GenAISummaryConfig genAISummaryConfig =
          this.inputGenAISummaryConfigResponse(GEN_AI_CONFIG_JSON);
      resultActions = this.mockMvc.perform(
          put("/v1/upsertGenAISummaryConfig").requestAttr("X-Username", USERNAME)
              .content(objectMapper.writeValueAsString(genAISummaryConfig))
              .contentType(MediaType.APPLICATION_JSON));
      MockHttpServletResponse response = resultActions.andReturn().getResponse();
      Assert.assertEquals(200, response.getStatus());
      MessageResponse messageResponse =
          objectMapper.readValue(response.getContentAsString(), MessageResponse.class);
      Assert.assertEquals(Constants.SUCCESS, messageResponse.getMessage());
    } catch (Exception e) {
      log.error("create gen ai config failed: ", e);
    }
  }

  @When("Gen AI : Transcript is Added")
  public void transcriptAdded() throws Exception {
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_STREAM_SUMMARY_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(GENAI_SUMMARY_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    mockGrpcClientResponse(1);
    ConversationEventEnvelope event = buildTranscriptAddedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("Gen AI : the transcript Added event should be processed successfully")
  public void verifyTranscript() throws InterruptedException {
    Thread.sleep(10000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    SummaryDoc doc = summaryDoc.get();
    System.out.println(doc);
    assertThat(doc.getConversationId()).isEqualTo(conversationId);
    assertThat(doc.getTenantId()).isEqualTo(tenantId);
    HeaderSummaryDoc headerSummary = doc.getHeaderSummary();
    assertThat(headerSummary).isNotNull();
    assertThat(headerSummary.getTemplate()).isEqualTo(EXPECTED_TEMPLATE);
    assertThat(headerSummary.getPlainText()).isEqualTo(EXPECTED_PLAINTEXT);
  }

  @When("Gen AI : Conversation Status")
  public void statusUpdate() throws Exception {
    //    UASSIST_STREAM_SUMMARY_FF
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_STREAM_SUMMARY_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_GEN_AI_FORMAT_DISABLED),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(GENAI_SUMMARY_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_STATE_V2_UPDATE_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    mockGrpcClientResponse(2);
    ConversationEventEnvelope event = buildStatusUpdateEvent();
    integrationTestHelper.publishKafka(event);
  }

  @When("Gen AI Conversation Status Update Event")
  public void GenAI_statusUpdate() throws Exception {
    mockGrpcClientResponse(3);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_STREAM_SUMMARY_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(ENABLE_GENAI_SUMMARY_ON_CONVERSATION_END),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(DISABLE_GENAI_SUMMARY_ON_TRANSCRIPT_ADD),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(false);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(GENAI_SUMMARY_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_GEN_AI_FORMAT_DISABLED),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_STATE_V2_UPDATE_FF),
        ArgumentMatchers.eq(FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildStatusUpdateEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("Gen AI : the conversationUpdate should be processed successfully")
  public void verifyConversationUpdate() throws InterruptedException {
    Thread.sleep(10000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    SummaryDoc summaryDoc1 = summaryDoc.get();
    assertThat(summaryDoc1.getGenAiSummary().getSections()).isNotNull();
    Assertions.assertFalse(summaryDoc1.getGenAiSummary().getSections().isEmpty());
    Assertions.assertEquals(2, summaryDoc1.getGenAiSummary().getSections().size());

  }

  private ConversationEventEnvelope buildTranscriptAddedEvent() {
    return ConversationEventEnvelope.newBuilder().setExternalConversationId(externalConversationId)
        .setEnvironment(environment).setTenantId(tenantId).setBusinessProcessId(bpId)
        .setExperienceId(expId).setConversationId(conversationId).setTranscriptChunkAdded(
            ConversationEventEnvelope.TranscriptChunkAdded.newBuilder(
                ConversationEventEnvelope.TranscriptChunkAdded.newBuilder().setTranscriptChunk(
                    TranscriptChunk.newBuilder().setLang(language).addTurns(
                        Turn.newBuilder().setParticipantType(participantType).setOrder(order)
                            .build()).build()).build()).build())
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildStatusUpdateEvent() {
    return ConversationEventEnvelope.newBuilder().setExternalConversationId(externalConversationId)
        .setEnvironment(environment).setTenantId(tenantId).setBusinessProcessId(bpId)
        .setExperienceId(expId).setConversationId(conversationId).setStateV2Updated(
            ConversationEventEnvelope.StateV2Updated.newBuilder().setState(
                    StateV2.newBuilder().setState(ConversationState.CONVERSATION_STATE_ENDED).build())
                .build()).setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ResultActions resultActions;

  private void mockGrpcClientResponse(int size) throws Exception {
    List<SectionText> sectionTextList = new java.util.ArrayList<>();
    for (int j = 0; j < size; j++) {
      sectionTextList.add(SectionText.newBuilder().setText(TEXT).setSection(
          Section.newBuilder().setId(ID_123).addEntities(
              SummaryEntity.newBuilder().setEntityLabel(ID_123)
                  .setEntityType(EntityType.ENTITY_TYPE_EMAIL.name()).build()).build()).build());
    }

    CreateGenerativeSummaryResponse createGenerativeSummaryResponse =
        CreateGenerativeSummaryResponse.newBuilder().addAllSectionText(sectionTextList).build();
    when(grpcClientHelper.getGenAiSummary(anyString(), anyString(), any(GenAISummaryCache.class),
        any(SummaryDoc.class),any())).thenReturn(Optional.of(createGenerativeSummaryResponse));
  }

  @When("Gen AI summary-reviewed")
  public void gen_ai_summary_reviewed() {
    try {
      this.mockTenantContext();
      resultActions = mockMvc.perform(
              post("/v1/agent/summary-reviewed").header("X-Tenant-Id", this.tenantId)
                  .param("conversationId", conversationId)  
                  .requestAttr(Constants.X_USERNAME, USERNAME)  // Fixed request attribute
                  .param("environment", this.environment).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());  // Validate response status
    } catch (Exception e) {
      log.error(" err ", e);
      throw new RuntimeException("Test failed due to exception: " + e.getMessage());
    }
  }

  public void mockTenantContext() {
    TenantContext.setTenantContext(tenantId, environment);
  }

  @Then("Gen AI : Validating summary Gen AI")
  public void genAIValidatingSummaryGenAI()
      throws UnsupportedEncodingException, JsonProcessingException {

    MockHttpServletResponse response = resultActions.andReturn().getResponse();
    Assert.assertEquals(200, response.getStatus());
    MessageResponse messageResponse =
        objectMapper.readValue(response.getContentAsString(), MessageResponse.class);
    Assert.assertEquals(Constants.SUCCESS, messageResponse.getMessage());
  }

  @When("Gen AI : summary-reviewed after transcript added")
  public void genAISummaryReviewedAfterTranscriptAdded() {
    log.debug("Gen AI : summary-reviewed after transcript adde");
    try {
      this.mockTenantContext();
      resultActions = mockMvc.perform(
              post("/v1/agent/summary-reviewed").header("X-Tenant-Id", this.tenantId)
                  .param("conversationId", conversationId)  
                  .requestAttr(Constants.X_USERNAME, USERNAME)  
                  .param("environment", this.environment).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk()); 
      doNothing().when(customMetricsPublisher).counterUpdate(anyString(), anyString());
    } catch (Exception e) {
      log.error(" err ", e);
      throw new RuntimeException("Test failed due to exception: " + e.getMessage());
    }
  }

  @Then("Gen AI : Validating summary Gen AI after transcript added")
  public void genAIValidatingSummaryGenAIAfterTranscriptAdded()
      throws InterruptedException, UnsupportedEncodingException, JsonProcessingException {
    MockHttpServletResponse response = resultActions.andReturn().getResponse();
    Assert.assertEquals(200, response.getStatus());
    MessageResponse messageResponse =
        objectMapper.readValue(response.getContentAsString(), MessageResponse.class);
    Assert.assertEquals(Constants.SUCCESS, messageResponse.getMessage());
  }

  @When("Gen AI : summary-reviewed auto-submit")
  public void genAISummaryReviewedAutoSubmit() {
    try {
      this.mockTenantContext();
      resultActions = mockMvc.perform(
              post("/v1/agent/summary-reviewed").header("X-Tenant-Id", this.tenantId)
                  .param("conversationId", conversationId)  
                  .requestAttr(Constants.X_USERNAME, USERNAME) 
                  .param("autosubmit", "true")  
                  .param("environment", this.environment).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());  // Validate response status
    } catch (Exception e) {
      log.error(" err ", e);
      throw new RuntimeException("Test failed due to exception: " + e.getMessage());
    }
  }

  @Then("Gen AI : Validating summary Gen AI auto-submit")
  public void genAIValidatingSummaryGenAIAutoSubmit() throws InterruptedException {
    Thread.sleep(10000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    SummaryDoc summaryDoc1 = summaryDoc.get();
    assertThat(summaryDoc1.getGenAiSummary().getSections()).isNotNull();
    Assertions.assertFalse(summaryDoc1.getGenAiSummary().getSections().isEmpty());
    Assertions.assertEquals(2, summaryDoc1.getGenAiSummary().getSections().size());
  }
}
