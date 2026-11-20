package com.uniphore.uassist.summary.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.conversations.v1.*;
import com.uniphore.llminference.v1.CreateGenerativeSummaryResponse;
import com.uniphore.llminference.v1.Section;
import com.uniphore.llminference.v1.SectionText;
import com.uniphore.summary.v1.Header;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.model.*;
import com.uniphore.uassist.summary.model.Disposition;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.*;

import static com.uniphore.uassist.summary.utils.TestConstant.CLASSPATH_JSON_CONFIG;
import static com.uniphore.uassist.summary.utils.TestConstant.TENANT_ID;

@TestComponent
@Slf4j
public class DataHelper {
  final ObjectMapper objectMapper = new ObjectMapper();

  public Disposition inputDispositionResponse(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(TestConstant.CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<Disposition>() {
        });
  }

  public GenAISummaryConfig inputGenAIConfigResponse(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(TestConstant.CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfig>() {
        });
  }

  public GenAISummaryConfigDoc inputGenAIConfigDocResponse(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(TestConstant.CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfigDoc>() {
        });
  }

  public SummaryDoc inputSummaryDocResponse(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(TestConstant.CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<SummaryDoc>() {
        });
  }

  public HeaderSummaryDoc inputHeaderSummaryDoc() {
    Map<String, String> maps = new HashMap<>();
    maps.put("key1", "Value1");
    return HeaderSummaryDoc.builder().plainText("plainText").template(
            "\"<h1 color='red' style='bold'>Address Details</h1><p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>customer name</span></p><p color='black' >mobile : <span variable-id='2' class='uniphore-variable-name'>mobile</span></p>\"")
        .entityInstanceIdMap(maps).variableInstanceIdMap(maps)
        .timestamp(TimeStampDoc.builder().seconds(1680202333).nanos(21314).build()).build();
  }

  public IntentSummaryDoc inputIntentSummaryDoc() {
    return IntentSummaryDoc.builder().intentId("12").instanceId("12").plainText("plaintext")
        .timestamp(TimeStampDoc.builder().build()).entityInstanceIdMap(Map.of("1", "1"))
        .variableInstanceIdMap(Map.of("1", "1")).source("USER").template(
            "<h1 color='red' style='bold'>Address Details</h1><p color='black'>customer name : <span entity-id='1' class='uniphore-entity-name'>customer name</span></p><p color='black' >mobile : <span variable-id='2' class='uniphore-variable-name'>mobile</span></p>\"")
        .disposition("disp").build();
  }

  public SummaryDoc inputSummaryDoc() {
    Map<String, IntentSummaryDoc> intentMap = new HashMap<>();
    intentMap.put("intent_add", inputIntentSummaryDoc());
    Map<String, IntentSummaryDoc> systemIntentSummaries = new HashMap<>();
    systemIntentSummaries.put("12", inputIntentSummaryDoc());
    Entity entity = Entity.newBuilder().setEntityId("entityId").setEntityInstanceId("instanceId")
        .setValue("entity1-1").setDetectedBy("USER").setEntityId("tenantId").build();
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    SummaryDoc.Slot slot =
        SummaryDoc.Slot.builder().id("slotId").instanceId("instanceId").value("values").build();
    entities.put("Slot1", slot);
    entities.put("ent1",
        SummaryDoc.Slot.builder().id("ent1").isEdited(true).instanceId("inst1").value("value1")
            .build());
    entities.put("ent2",
        SummaryDoc.Slot.builder().id("ent2").isEdited(true).instanceId("inst2").value("value2")
            .build());
    entities.put("ent3",
        SummaryDoc.Slot.builder().id("ent3").isEdited(false).instanceId("inst3").value("value3")
            .build());
    entities.put("ent4",
        SummaryDoc.Slot.builder().id("ent4").isEdited(false).instanceId("inst4").value("value4")
            .build());

    GenAiSummaryDoc.Section section =
        GenAiSummaryDoc.Section.builder().id("id").text("text section").build();
    List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
    sectionList.add(section);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("EventEndTime", (173 * 1000L));
    metadata.put("agent_turns", 6);
    metadata.put("customer_turns", 6);
    Map<String, Set<String>> unmappedVariableInGenAISummary =
        Map.of("wireless phone upgrade", Set.of("call-reasons", "call-outcome"));
    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList)
        .unmappedVariableInGenAiSummaries(unmappedVariableInGenAISummary).build();
    return SummaryDoc.builder().conversationId("ConvId").tenantId("tenantId").environment("dev")
        .businessProcessId("bp").language("en").experienceId("exp1")
        .headerSummary(inputHeaderSummaryDoc()).intentSummaries(intentMap).entities(entities)
        .variables(entities).agentNotes(null).participants(null)
        .systemIntentSummaries(systemIntentSummaries).metadata(metadata)
        .timestamp(TimeStampDoc.builder().nanos(1234512).seconds(13).build())
        .genAiSummary(genAiSummaryDoc).templateId("templateId").templateVersion(123).build();
  }

  public SummaryDoc inputSummaryDocWithNullTemplateId() {
    Map<String, IntentSummaryDoc> intentMap = new HashMap<>();
    intentMap.put("intent_add", inputIntentSummaryDoc());
    Map<String, IntentSummaryDoc> systemIntentSummaries = new HashMap<>();
    systemIntentSummaries.put("12", inputIntentSummaryDoc());
    Entity entity = Entity.newBuilder().setEntityId("entityId").setEntityInstanceId("instanceId")
        .setValue("entity1-1").setDetectedBy("USER").setEntityId("tenantId").build();
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    SummaryDoc.Slot slot =
        SummaryDoc.Slot.builder().id("slotId").instanceId("instanceId").value("values").build();
    entities.put("Slot1", slot);
    entities.put("ent1",
        SummaryDoc.Slot.builder().id("ent1").isEdited(true).instanceId("inst1").value("value1")
            .build());
    entities.put("ent2",
        SummaryDoc.Slot.builder().id("ent2").isEdited(true).instanceId("inst2").value("value2")
            .build());
    entities.put("ent3",
        SummaryDoc.Slot.builder().id("ent3").isEdited(false).instanceId("inst3").value("value3")
            .build());
    entities.put("ent4",
        SummaryDoc.Slot.builder().id("ent4").isEdited(false).instanceId("inst4").value("value4")
            .build());

    GenAiSummaryDoc.Section section =
        GenAiSummaryDoc.Section.builder().id("id").text("text section").build();
    List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
    sectionList.add(section);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("EventEndTime", (173 * 1000L));
    metadata.put("agent_turns", 6);
    metadata.put("customer_turns", 6);
    Map<String, Set<String>> unmappedVariableInGenAISummary =
        Map.of("wireless phone upgrade", Set.of("call-reasons", "call-outcome"));
    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList)
        .unmappedVariableInGenAiSummaries(unmappedVariableInGenAISummary).build();
    return SummaryDoc.builder().conversationId("ConvId").tenantId("tenantId").environment("dev")
        .businessProcessId("bp").language("en").experienceId("exp1")
        .headerSummary(inputHeaderSummaryDoc()).intentSummaries(intentMap).entities(entities)
        .variables(entities).agentNotes(null).participants(null)
        .systemIntentSummaries(systemIntentSummaries).metadata(metadata)
        .timestamp(TimeStampDoc.builder().nanos(1234512).seconds(13).build())
        .genAiSummary(genAiSummaryDoc).templateId(null).templateVersion(null).build();
  }

  public AgentRequestPayload inputAgentNotesRequestPayload(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<AgentRequestPayload>() {
        });
  }

  public GenAISummaryCache inputGenAISummaryResponse(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryCache>() {
        });
  }

  public GenAISummaryConfig inputGenAISummaryConfigResponse(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfig>() {
        });
  }

  public SummaryResponse summaryResponseBuilder() {
    HeaderResponse headerResponse = HeaderResponse.builder().summary("Header Response Summary")
        .timestamp(DateUtil.getTimestamp()).build();
    IntentResponse intentResponse =
        IntentResponse.builder().intentId("intent1").summary("Intent reponse summary")
            .disposition("disp1").source("agent").timestamp(DateUtil.getTimestamp())
            .instanceId("instance1").build();
    List<IntentResponse> intentResponseList = new ArrayList<>();
    intentResponseList.add(intentResponse);
    ParticipantResponse participantResponse =
        ParticipantResponse.builder().participantId("p1").type("type1").ctiAgentId("123").build();
    List<ParticipantResponse> participantResponses = new ArrayList<>();
    participantResponses.add(participantResponse);
    GenAiSummaryDoc.Section section =
        GenAiSummaryDoc.Section.builder().text("test").id("123").build();
    List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
    sectionList.add(section);
    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
    return SummaryResponse.builder().conversationId("conv1").businessProcessId("bp1")
        .tenantId(TENANT_ID).environment("dev").agentNote("Notes").language("us-en")
        .header(headerResponse).intents(intentResponseList).participants(participantResponses)
        .genAiSummary(genAiSummaryDoc).systemIntents(intentResponseList).build();
  }

  public SummaryDoc inputSummaryDocWithSystemSummary() {
    Map<String, IntentSummaryDoc> intentMap = new HashMap<>();
    intentMap.put(Constants.TAG_ENTITY_ADD, inputIntentSummaryDoc());
    Map<String, IntentSummaryDoc> systemIntentSummaries = new HashMap<>();
    systemIntentSummaries.put(Constants.TAG_ENTITY_ADD, inputIntentSummaryDoc());
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

    AgentNotesDoc agentNotesDoc = AgentNotesDoc.builder().plainText("plaintext").text("text")
        .timestamp(TimeStampDoc.builder().nanos(1342).seconds(733547).build()).build();

    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
    return SummaryDoc.builder().conversationId("ConvId").tenantId("tenantId").environment("dev")
        .businessProcessId("bp").language("en").experienceId("exp1").agentNotes(agentNotesDoc)
        .headerSummary(inputHeaderSummaryDoc()).intentSummaries(intentMap).entities(entities)
        .variables(entities).agentNotes(null).participants(null)
        .systemIntentSummaries(systemIntentSummaries)
        .timestamp(TimeStampDoc.builder().nanos(1234512).seconds(63531).build())
        .genAiSummary(genAiSummaryDoc).build();
  }

  public ConversationEventEnvelope createTranscriptAddedEvent() {
    ConversationEventEnvelope conversationEventEnvelope =
        ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
            .setConversationId("123").setTranscriptAdded(
                ConversationEventEnvelope.TranscriptAdded.newBuilder()
                    .setTranscript(Transcript.newBuilder().build()).build())
            .setBusinessProcessId("test-bp").setExperienceId("exp1")
            .setTimestamp(DateUtil.getCurrentTimestamp()).build();
    return conversationEventEnvelope;
  }

  public ConversationEventEnvelope createTranscriptChuckAddedEvent() {
    return ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
        .setConversationId("123").setTranscriptChunkAdded(
            ConversationEventEnvelope.TranscriptChunkAdded.newBuilder().setTranscriptChunk(
                TranscriptChunk.newBuilder().addTurns(
                    Turn.newBuilder().setChunkId(432).setEndOffset(95).setOrder(1)
                        .setParticipantType("agent").setStartOffset(14).addWords(
                            Word.newBuilder().setText("hi hello").setStartOffset(67).setEndOffset(89)
                                .build()).addUtterances(Utterance.newBuilder().setText("txt").build())
                        .build()).build()).build()).setBusinessProcessId("test-bp")
        .setExperienceId("exp1").setTimestamp(DateUtil.getCurrentTimestamp()).build();
  }

  public ConversationEventEnvelope createTranscriptChuckAddedEventWithNull() {
    return ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
        .setConversationId("123").setTranscriptChunkAdded(
            ConversationEventEnvelope.TranscriptChunkAdded.newBuilder().setTranscriptChunk(
                TranscriptChunk.newBuilder().addTurns(
                    Turn.newBuilder().setChunkId(432).setEndOffset(95).setOrder(1)
                        .setParticipantType("customer").setStartOffset(14).addWords(
                            Word.newBuilder().setText("hi hello").setStartOffset(67).setEndOffset(89)
                                .build()).addUtterances(Utterance.newBuilder().setText("txt").build())
                        .build()).build()).build()).setBusinessProcessId("test-bp")
        .setExperienceId("exp1").setTimestamp(DateUtil.getCurrentTimestamp()).build();
  }

  public ConversationEventEnvelope createTranscriptChuckAddedEventWithEmptyTurns() {
    return ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
        .setConversationId("123").setTranscriptChunkAdded(
            ConversationEventEnvelope.TranscriptChunkAdded.newBuilder()
                .setTranscriptChunk(TranscriptChunk.newBuilder().build()).build())
        .setBusinessProcessId("test-bp").setExperienceId("exp1")
        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
  }

  public ConversationEventEnvelope createStateUpdatedEvent() {
    ConversationEventEnvelope conversationEventEnvelope =
        ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
            .setConversationId("123").setStateV2Updated(
                ConversationEventEnvelope.StateV2Updated.newBuilder().setState(
                        StateV2.newBuilder().setState(ConversationState.CONVERSATION_STATE_ENDED).build())
                    .build()).setBusinessProcessId("test-bp").setExperienceId("exp1")
            .setTimestamp(DateUtil.getCurrentTimestamp()).build();
    return conversationEventEnvelope;
  }

  public ConversationEventEnvelope createStateV1UpdatedEvent() {
    ConversationEventEnvelope conversationEventEnvelope =
        ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
            .setConversationId("123").setStateUpdated(
                ConversationEventEnvelope.StateUpdated.newBuilder().setState(
                        State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ENDED).build())
                    .build()).setBusinessProcessId("test-bp").setExperienceId("exp1")
            .setTimestamp(DateUtil.getCurrentTimestamp()).build();
    return conversationEventEnvelope;
  }

  public ConversationEventEnvelope createPolicyAddedEvent() {
    ConversationEventEnvelope conversationEventEnvelope =
        ConversationEventEnvelope.newBuilder().setTenantId("12345").setEnvironment("dev")
            .setConversationId("123").setPoliciesAdded(
                ConversationEventEnvelope.PoliciesAdded.newBuilder().addPolicies(
                    Policy.newBuilder().setPolicyId("policy1")
                        .setActionType(PolicyActionType.POLICY_ACTION_TYPE_LOCK)).build()).addPolicies(
                Policy.newBuilder().setActionType(PolicyActionType.POLICY_ACTION_TYPE_BLOCKLIST)
                    .setPolicyId("policy1").build()).setBusinessProcessId("test-bp")
            .setExperienceId("exp1").setTimestamp(DateUtil.getCurrentTimestamp()).build();
    return conversationEventEnvelope;
  }

  public Map<String, String> jsonToMapConverter(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(TestConstant.CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<Map<String, String>>() {
        });
  }

  public GenAISummaryCache inputGenAiConfig(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryCache>() {
        });
  }

  public GenAISummaryConfig inputGenAiConfigObject(final String fileName) throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<GenAISummaryConfig>() {
        });
  }

  public AgentRequestPayload inputAgentRequestPayloadResponse(final String fileName)
      throws IOException {
    final File file = ResourceUtils.getFile(CLASSPATH_JSON_CONFIG + fileName);
    return this.objectMapper.readValue(Files.readAllBytes(file.toPath()),
        new TypeReference<AgentRequestPayload>() {
        });
  }

  public CreateGenerativeSummaryResponse summaryResponse() {
    List<SectionText> sectionTexts = new ArrayList<>();
    SectionText section = SectionText.newBuilder().setSection(
            Section.newBuilder().setId(Constants.CALL_REASON).putAllDirectives(new HashMap<>()).build())
        .setText("dummy summary").build();
    sectionTexts.add(section);
    section = SectionText.newBuilder().setSection(
        Section.newBuilder().setId(Constants.AGENT_ACTIONS).putAllDirectives(new HashMap<>())
            .build()).setText("dummy summary").build();
    sectionTexts.add(section);
    section = SectionText.newBuilder().setSection(
        Section.newBuilder().setId(Constants.CALL_OUTCOME).putAllDirectives(new HashMap<>())
            .build()).setText("dummy summary").build();
    sectionTexts.add(section);
    section = SectionText.newBuilder().setSection(
        Section.newBuilder().setId(Constants.CUSTOMER_SENTIMENT).putAllDirectives(new HashMap<>())
            .build()).setText("dummy summary").build();
    sectionTexts.add(section);
    return CreateGenerativeSummaryResponse.newBuilder().addAllSectionText(sectionTexts).build();
  }

  public CreateGenerativeSummaryResponse summaryResponseFormat() {
    List<SectionText> sectionTexts = new ArrayList<>();
    SectionText section = SectionText.newBuilder().setSection(
            Section.newBuilder().setId(Constants.CALL_REASON).putAllDirectives(new HashMap<>()).build())
        .setText("dummy summary 1.dummay summary 2. dummy summary 3.").build();
    sectionTexts.add(section);
    section = SectionText.newBuilder().setSection(
        Section.newBuilder().setId(Constants.AGENT_ACTIONS).putAllDirectives(new HashMap<>())
            .build()).setText("dummy summary 1.dummay summary 2. dummy summary 3.").build();
    sectionTexts.add(section);
    section = SectionText.newBuilder().setSection(
        Section.newBuilder().setId(Constants.CALL_OUTCOME).putAllDirectives(new HashMap<>())
            .build()).setText("dummy summary 1.dummay summary 2. dummy summary 3.").build();
    sectionTexts.add(section);
    section = SectionText.newBuilder().setSection(
        Section.newBuilder().setId(Constants.CUSTOMER_SENTIMENT).putAllDirectives(new HashMap<>())
            .build()).setText("dummy summary 1.dummay summary 2. dummy summary 3.").build();
    sectionTexts.add(section);
    return CreateGenerativeSummaryResponse.newBuilder().addAllSectionText(sectionTexts).build();
  }

  public Header inputHeader() {
    return Header.newBuilder().setPlainText("plainText").setTemplate("<p>plainText</p>")
        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
  }

  public IntentTemplate inputIntentTemplate() {
    return IntentTemplate.builder().intentTemplateId("intentTemp1").template("plaiinText")
        .intentId("i1").experienceId("exp1").parsedTemplate("plainText").businessProcessId("bp1")
        .tenantId(TENANT_ID).environment("dev").deleted(false).userId("user1").build();
  }

  public IntentTemplateDoc inputIntentTemplateDoc() {
    return IntentTemplateDoc.builder().id("id").fieldId("fieldId").intentId("intentId")
        .template("template").businessProcessId("bp").experienceId("exp").environment("dev")
        .tenantId("tenantId").deleted(false).userId("userId").createdTimestamp("timestamp")
        .modifiedTimestamp("timestamp").build();
  }

}
