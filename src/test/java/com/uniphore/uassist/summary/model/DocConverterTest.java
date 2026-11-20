package com.uniphore.uassist.summary.model;

import com.google.protobuf.Timestamp;
import com.uniphore.conversations.v1.Conversation;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.ConversationState;
import com.uniphore.conversations.v1.Participant;
import com.uniphore.conversations.v1.State;
import com.uniphore.conversations.v1.StateV2;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.dao.documents.AgentEditsDoc;
import com.uniphore.uassist.summary.dao.documents.AgentNotesDoc;
import com.uniphore.uassist.summary.dao.documents.AgentStepsDoc;
import com.uniphore.uassist.summary.dao.documents.DispositionDoc;
import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.HeaderSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.HeaderTemplateDoc;
import com.uniphore.uassist.summary.dao.documents.IntentSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.dao.documents.TimeStampDoc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocConverterTest {

  private static final String CONVERSATION_ID = "conv-id";
  private static final String TENANT_ID = "tenant-id";
  private static final String ENVIRONMENT = "dev";
  private static final String BUSINESS_PROCESS_ID = "bp-id";
  private static final String EXPERIENCE_ID = "exp-id";
  private static final String EXT_CONVERSATION_ID = "ext-conv-id";

  // ---------- getSummaryDoc(String, String, String) ----------

  @Test
  void testGetSummaryDoc_fromStrings() {
    TenantContext.setTenantContext(TENANT_ID, ENVIRONMENT);

    SummaryDoc summaryDoc =
        DocConverter.getSummaryDoc(CONVERSATION_ID, BUSINESS_PROCESS_ID, "<p>header</p>");

    assertEquals(CONVERSATION_ID, summaryDoc.getConversationId());
    assertEquals(TENANT_ID, summaryDoc.getTenantId());
    assertEquals(ENVIRONMENT, summaryDoc.getEnvironment());
    assertEquals(BUSINESS_PROCESS_ID, summaryDoc.getBusinessProcessId());
    assertEquals("<p>header</p>", summaryDoc.getHeaderSummary().getTemplate());
    assertEquals("header", summaryDoc.getHeaderSummary().getPlainText());
  }

  // ---------- getSummaryDoc(ConversationEventEnvelope, String, boolean) ----------

  @Test
  void testGetSummaryDoc_fromEnvelope_stateV1_usesActiveStateTimestamp() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Timestamp activeStateTimestamp = Timestamp.newBuilder().setSeconds(1000).build();
    Conversation conversation = Conversation.newBuilder()
        .addStates(State.newBuilder().setState(ConversationState.CONVERSATION_STATE_INIT)
            .setTimestamp(Timestamp.newBuilder().setSeconds(500).build()).build())
        .addStates(State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ACTIVE)
            .setTimestamp(activeStateTimestamp).build())
        .build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", false);

    assertEquals(1000L, summaryDoc.getTimestamp().getSeconds());
  }

  @Test
  void testGetSummaryDoc_fromEnvelope_stateV2_usesActiveStateTimestamp() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Timestamp activeStateTimestamp = Timestamp.newBuilder().setSeconds(1500).setNanos(250).build();
    Conversation conversation = Conversation.newBuilder()
        .addStatesV2(StateV2.newBuilder().setState(ConversationState.CONVERSATION_STATE_ACTIVE)
            .setTimestamp(activeStateTimestamp).build())
        .build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", true);

    assertEquals(1500L, summaryDoc.getTimestamp().getSeconds());
    assertEquals(250, summaryDoc.getTimestamp().getNanos());
  }

  @Test
  void testGetSummaryDoc_fromEnvelope_stateV1_noActiveState_fallsBackToEventTimestamp() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Conversation conversation = Conversation.newBuilder()
        .addStates(State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ENDED)
            .setTimestamp(Timestamp.newBuilder().setSeconds(500).build()).build())
        .build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", false);

    assertEquals(2000L, summaryDoc.getTimestamp().getSeconds());
  }

  @Test
  void testGetSummaryDoc_fromEnvelope_stateV2_noStates_fallsBackToEventTimestamp() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Conversation conversation = Conversation.newBuilder().build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", true);

    assertEquals(2000L, summaryDoc.getTimestamp().getSeconds());
  }

  @Test
  void testGetSummaryDoc_fromEnvelope_ignoresV1StatesWhenV2FfEnabled() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Conversation conversation = Conversation.newBuilder()
        .addStates(State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ACTIVE)
            .setTimestamp(Timestamp.newBuilder().setSeconds(1000).build()).build())
        .build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    // isStateV2FfEnabled=true, but only the legacy `states` list has the ACTIVE entry
    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", true);

    assertEquals(2000L, summaryDoc.getTimestamp().getSeconds());
  }

  @Test
  void testGetSummaryDoc_fromEnvelope_headerSummaryTimestampAlwaysUsesEventTimestamp() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Timestamp activeStateTimestamp = Timestamp.newBuilder().setSeconds(1000).build();
    Conversation conversation = Conversation.newBuilder()
        .addStates(State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ACTIVE)
            .setTimestamp(activeStateTimestamp).build())
        .build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", false);

    // header summary timestamp is unaffected by the telephony call-start resolution
    assertEquals(2000L, summaryDoc.getHeaderSummary().getTimestamp().getSeconds());
    assertEquals(1000L, summaryDoc.getTimestamp().getSeconds());
  }

  @Test
  void testGetSummaryDoc_fromEnvelope_basicFieldsAndParticipants() {
    Timestamp eventTimestamp = Timestamp.newBuilder().setSeconds(2000).build();
    Conversation conversation = Conversation.newBuilder().setLanguage("en")
        .setExternalConversationId(EXT_CONVERSATION_ID)
        .addParticipants(Participant.newBuilder().setParticipantId("p1").setType("agent")
            .setCtiAgentId("cti-1").build())
        .build();
    ConversationEventEnvelope envelope = baseEnvelopeBuilder(eventTimestamp, conversation).build();

    SummaryDoc summaryDoc = DocConverter.getSummaryDoc(envelope, "<p>header</p>", false);

    assertEquals(CONVERSATION_ID, summaryDoc.getConversationId());
    assertEquals(TENANT_ID, summaryDoc.getTenantId());
    assertEquals(ENVIRONMENT, summaryDoc.getEnvironment());
    assertEquals(BUSINESS_PROCESS_ID, summaryDoc.getBusinessProcessId());
    assertEquals(EXPERIENCE_ID, summaryDoc.getExperienceId());
    assertEquals("en", summaryDoc.getLanguage());
    assertEquals(EXT_CONVERSATION_ID, summaryDoc.getExtConversationId());
    assertNotNull(summaryDoc.getGenAiSummary());
    assertTrue(summaryDoc.getGenAiSummary().getSections().isEmpty());
    assertTrue(summaryDoc.getIntentSummaries().isEmpty());
    assertEquals(1, summaryDoc.getParticipants().size());
    assertEquals("p1", summaryDoc.getParticipants().get(0).getParticipantId());
    assertEquals("agent", summaryDoc.getParticipants().get(0).getType());
    assertEquals("cti-1", summaryDoc.getParticipants().get(0).getCtiAgentId());
  }

  private ConversationEventEnvelope.Builder baseEnvelopeBuilder(Timestamp eventTimestamp,
      Conversation conversation) {
    return ConversationEventEnvelope.newBuilder().setConversationId(CONVERSATION_ID)
        .setTenantId(TENANT_ID).setEnvironment(ENVIRONMENT)
        .setBusinessProcessId(BUSINESS_PROCESS_ID).setExperienceId(EXPERIENCE_ID)
        .setTimestamp(eventTimestamp).setConversationStarted(
            ConversationEventEnvelope.ConversationStarted.newBuilder()
                .setConversation(conversation).build());
  }

  // ---------- agentNotestoSummaryDoc ----------

  @Test
  void testAgentNotestoSummaryDoc() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    AgentRequestPayload payload =
        AgentRequestPayload.builder().agentNotes("<p>notes</p>").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(100).build();

    SummaryDoc result = DocConverter.agentNotestoSummaryDoc(summaryDoc, payload, timestamp);

    assertNotNull(result.getAgentNotes());
    assertEquals("<p>notes</p>", result.getAgentNotes().getText());
    // agentNotestoSummaryDoc copies the raw text into plainText verbatim (no HTML stripping)
    assertEquals("<p>notes</p>", result.getAgentNotes().getPlainText());
    assertEquals(100L, result.getAgentNotes().getTimestamp().getSeconds());
  }

  // ---------- addIntenttoSummaryDoc ----------

  @Test
  void testAddIntenttoSummaryDoc_createsMapWhenNull() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    summaryDoc.setIntentSummaries(null);
    AgentRequestPayload payload =
        AgentRequestPayload.builder().intentId("intent-1").intentSummary("<p>summary</p>")
            .source("agent").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(100).build();

    SummaryDoc result =
        DocConverter.addIntenttoSummaryDoc(summaryDoc, payload, timestamp, "inst-1");

    assertNotNull(result.getIntentSummaries());
    IntentSummaryDoc intentSummaryDoc = result.getIntentSummaries().get("inst-1");
    assertNotNull(intentSummaryDoc);
    assertEquals("intent-1", intentSummaryDoc.getIntentId());
    assertEquals("agent", intentSummaryDoc.getSource());
    assertEquals("inst-1", intentSummaryDoc.getInstanceId());
    assertEquals("<p>summary</p>", intentSummaryDoc.getTemplate());
    assertEquals("summary", intentSummaryDoc.getPlainText());
  }

  // ---------- agentEditstoSummaryDoc ----------

  @Test
  void testAgentEditstoSummaryDoc_intentEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    Map<String, IntentSummaryDoc> intentSummaries = new LinkedHashMap<>();
    intentSummaries.put("inst-1",
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1").source("agent")
            .build());
    summaryDoc.setIntentSummaries(intentSummaries);
    AgentRequestPayload payload =
        AgentRequestPayload.builder().instanceId("inst-1").intentId("intent-1")
            .intentSummary("<p>edited</p>").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(200).build();

    SummaryDoc result = DocConverter.agentEditstoSummaryDoc(summaryDoc, payload, timestamp);

    IntentSummaryDoc edited = result.getIntentSummaries().get("inst-1");
    assertEquals("<p>edited</p>", edited.getTemplate());
    assertEquals("edited", edited.getPlainText());
  }

  @Test
  void testAgentEditstoSummaryDoc_headerEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    AgentRequestPayload payload =
        AgentRequestPayload.builder().headerSummary("<p>new header</p>").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(200).build();

    SummaryDoc result = DocConverter.agentEditstoSummaryDoc(summaryDoc, payload, timestamp);

    assertEquals("<p>new header</p>", result.getHeaderSummary().getTemplate());
    assertEquals("new header", result.getHeaderSummary().getPlainText());
  }

  @Test
  void testAgentEditstoSummaryDoc_dispositionEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    Map<String, IntentSummaryDoc> intentSummaries = new LinkedHashMap<>();
    intentSummaries.put("inst-1",
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1")
            .template("<p>orig</p>").build());
    summaryDoc.setIntentSummaries(intentSummaries);
    AgentRequestPayload payload =
        AgentRequestPayload.builder().instanceId("inst-1").disposition("resolved").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(200).build();

    SummaryDoc result = DocConverter.agentEditstoSummaryDoc(summaryDoc, payload, timestamp);

    assertEquals("resolved", result.getIntentSummaries().get("inst-1").getDisposition());
  }

  @Test
  void testAgentEditstoSummaryDoc_genAiSummaryEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    GenAiSummaryDoc editedGenAiSummary =
        GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    AgentRequestPayload payload =
        AgentRequestPayload.builder().genAiSummaryDoc(editedGenAiSummary).build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(200).build();

    SummaryDoc result = DocConverter.agentEditstoSummaryDoc(summaryDoc, payload, timestamp);

    assertEquals(editedGenAiSummary, result.getGenAiSummary());
  }

  // ---------- agentAgentEditsDoc (AgentRequestPayload overload) ----------

  @Test
  void testAgentAgentEditsDoc_intentEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    Map<String, IntentSummaryDoc> intentSummaries = new LinkedHashMap<>();
    intentSummaries.put("inst-1",
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1")
            .template("<p>orig</p>").build());
    summaryDoc.setIntentSummaries(intentSummaries);
    AgentRequestPayload payload = AgentRequestPayload.builder().instanceId("inst-1")
        .intentId("intent-1").intentSummary("<p>edited</p>").agentId("agent-1").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(300).build();

    AgentEditsDoc result = DocConverter.agentAgentEditsDoc(summaryDoc, payload, timestamp,
        Commons.AgentAction.INTENT_EDIT.toString());

    assertEquals(CONVERSATION_ID, result.getConversationId());
    assertEquals("intent-1", result.getIntentId());
    assertEquals("inst-1", result.getInstanceId());
    assertEquals("<p>orig</p>", result.getText());
    assertEquals("<p>edited</p>", result.getEditedText());
    assertEquals("edited", result.getEditedPlainText());
    assertEquals("agent-1", result.getAgentId());
    assertEquals(Commons.AgentAction.INTENT_EDIT.toString(), result.getAction());
  }

  @Test
  void testAgentAgentEditsDoc_headerEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    summaryDoc.setHeaderSummary(HeaderSummaryDoc.builder().template("<p>orig header</p>").build());
    AgentRequestPayload payload =
        AgentRequestPayload.builder().headerSummary("<p>new header</p>").agentId("agent-1").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(300).build();

    AgentEditsDoc result = DocConverter.agentAgentEditsDoc(summaryDoc, payload, timestamp,
        Commons.AgentAction.HEADER_EDIT.toString());

    assertEquals("<p>orig header</p>", result.getText());
    assertEquals("<p>new header</p>", result.getEditedText());
    assertNull(result.getIntentId());
  }

  @Test
  void testAgentAgentEditsDoc_intentAdd() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    AgentRequestPayload payload = AgentRequestPayload.builder().intentId("intent-1")
        .instanceId("inst-1").agentId("agent-1").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(300).build();

    AgentEditsDoc result = DocConverter.agentAgentEditsDoc(summaryDoc, payload, timestamp,
        Commons.AgentAction.INTENT_ADD.toString());

    assertEquals("intent-1", result.getIntentId());
    assertEquals("inst-1", result.getInstanceId());
    assertNull(result.getText());
    assertNull(result.getEditedText());
  }

  @Test
  void testAgentAgentEditsDoc_genAiSummaryEdit() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    GenAiSummaryDoc editedGenAiSummary =
        GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    AgentRequestPayload payload = AgentRequestPayload.builder().intentId("intent-1")
        .instanceId("inst-1").genAiSummaryDoc(editedGenAiSummary).agentId("agent-1").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(300).build();

    AgentEditsDoc result = DocConverter.agentAgentEditsDoc(summaryDoc, payload, timestamp,
        Commons.AgentAction.GEN_AI_SUMMARY_EDIT.toString());

    assertEquals(summaryDoc.getGenAiSummary().toString(), result.getGenAiText());
    assertEquals(editedGenAiSummary.toString(), result.getGenAiTextEdited());
  }

  // ---------- agentAgentEditsDoc (IntentSummaryDoc overload) ----------

  @Test
  void testAgentAgentEditsDoc_intentSummaryDocOverload_intentAdd() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    IntentSummaryDoc intentSummaryDoc =
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(300).build();

    AgentEditsDoc result = DocConverter.agentAgentEditsDoc(summaryDoc, intentSummaryDoc, timestamp,
        Commons.AgentAction.INTENT_ADD.toString());

    assertEquals("intent-1", result.getIntentId());
    assertEquals("inst-1", result.getInstanceId());
  }

  @Test
  void testAgentAgentEditsDoc_intentSummaryDocOverload_unhandledActionReturnsEmpty() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    IntentSummaryDoc intentSummaryDoc =
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1").build();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(300).build();

    AgentEditsDoc result = DocConverter.agentAgentEditsDoc(summaryDoc, intentSummaryDoc, timestamp,
        Commons.AgentAction.HEADER_EDIT.toString());

    assertNull(result.getIntentId());
    assertNull(result.getConversationId());
  }

  // ---------- agentDeleteIntenttoSummaryDoc ----------

  @Test
  void testAgentDeleteIntenttoSummaryDoc_removesExistingEntry() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    Map<String, IntentSummaryDoc> intentSummaries = new LinkedHashMap<>();
    intentSummaries.put("inst-1", IntentSummaryDoc.builder().instanceId("inst-1").build());
    intentSummaries.put("inst-2", IntentSummaryDoc.builder().instanceId("inst-2").build());
    summaryDoc.setIntentSummaries(intentSummaries);

    SummaryDoc result = DocConverter.agentDeleteIntenttoSummaryDoc(summaryDoc, "inst-1");

    assertFalse(result.getIntentSummaries().containsKey("inst-1"));
    assertTrue(result.getIntentSummaries().containsKey("inst-2"));
  }

  @Test
  void testAgentDeleteIntenttoSummaryDoc_nullMapCreatesEmptyMap() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    summaryDoc.setIntentSummaries(null);

    SummaryDoc result = DocConverter.agentDeleteIntenttoSummaryDoc(summaryDoc, "inst-1");

    assertNotNull(result.getIntentSummaries());
    assertTrue(result.getIntentSummaries().isEmpty());
  }

  // ---------- toHeaderTemplateDoc / toHeaderTemplate / toHeaderTemplates ----------

  @Test
  void testToHeaderTemplateDoc_createsNewWhenAbsent() {
    TenantContext.setTenantContext(TENANT_ID, ENVIRONMENT);
    HeaderTemplate headerTemplate = HeaderTemplate.builder().businessProcessId(BUSINESS_PROCESS_ID)
        .template("<p>template</p>").userId("user-1").build();

    HeaderTemplateDoc result =
        DocConverter.toHeaderTemplateDoc(Optional.empty(), headerTemplate, "100");

    assertEquals(BUSINESS_PROCESS_ID, result.getBusinessProcessId());
    assertEquals(TENANT_ID, result.getTenantId());
    assertEquals(ENVIRONMENT, result.getEnvironment());
    assertEquals("<p>template</p>", result.getTemplate());
    assertEquals("100", result.getCreatedTimestamp());
    assertEquals("100", result.getModifiedTimestamp());
    assertNotNull(result.getFieldId());
  }

  @Test
  void testToHeaderTemplateDoc_updatesWhenPresent() {
    HeaderTemplateDoc existing =
        HeaderTemplateDoc.builder().fieldId("field-1").businessProcessId(BUSINESS_PROCESS_ID)
            .template("<p>old</p>").createdTimestamp("50").build();
    HeaderTemplate headerTemplate =
        HeaderTemplate.builder().template("<p>new</p>").userId("user-2").build();

    HeaderTemplateDoc result =
        DocConverter.toHeaderTemplateDoc(Optional.of(existing), headerTemplate, "200");

    assertEquals("field-1", result.getFieldId());
    assertEquals("<p>new</p>", result.getTemplate());
    assertEquals("user-2", result.getUserId());
    assertEquals("50", result.getCreatedTimestamp());
    assertEquals("200", result.getModifiedTimestamp());
  }

  @Test
  void testToHeaderTemplate_presentMapsFields() {
    HeaderTemplateDoc headerTemplateDoc = HeaderTemplateDoc.builder().fieldId("field-1")
        .businessProcessId(BUSINESS_PROCESS_ID).tenantId(TENANT_ID).environment(ENVIRONMENT)
        .template("<p>template</p>").build();

    HeaderTemplate result = DocConverter.toHeaderTemplate(Optional.of(headerTemplateDoc));

    assertEquals("field-1", result.getHeaderTemplateId());
    assertEquals(BUSINESS_PROCESS_ID, result.getBusinessProcessId());
    assertEquals(TENANT_ID, result.getTenantId());
    assertEquals(ENVIRONMENT, result.getEnvironment());
    assertEquals("<p>template</p>", result.getTemplate());
    assertEquals("template", result.getParsedTemplate());
  }

  @Test
  void testToHeaderTemplate_absentReturnsEmpty() {
    HeaderTemplate result = DocConverter.toHeaderTemplate(Optional.empty());

    assertNull(result.getHeaderTemplateId());
    assertNull(result.getTemplate());
  }

  @Test
  void testToHeaderTemplates_mapsList() {
    List<HeaderTemplateDoc> docs = List.of(
        HeaderTemplateDoc.builder().fieldId("field-1").template("<p>a</p>").build(),
        HeaderTemplateDoc.builder().fieldId("field-2").template("<p>b</p>").build());

    List<HeaderTemplate> result = DocConverter.toHeaderTemplates(docs);

    assertEquals(2, result.size());
    assertEquals("field-1", result.get(0).getHeaderTemplateId());
    assertEquals("field-2", result.get(1).getHeaderTemplateId());
  }

  // ---------- toIntentTemplateDoc / toIntentTemplate / toIntentTemplates ----------

  @Test
  void testToIntentTemplateDoc_createsNewWhenAbsent() {
    TenantContext.setTenantContext(TENANT_ID, ENVIRONMENT);
    IntentTemplate intentTemplate =
        IntentTemplate.builder().intentId("intent-1").template("<p>template</p>")
            .businessProcessId(BUSINESS_PROCESS_ID).experienceId(EXPERIENCE_ID).userId("user-1")
            .build();

    IntentTemplateDoc result =
        DocConverter.toIntentTemplateDoc(Optional.empty(), intentTemplate, "100");

    assertEquals("intent-1", result.getIntentId());
    assertEquals(BUSINESS_PROCESS_ID, result.getBusinessProcessId());
    assertEquals(EXPERIENCE_ID, result.getExperienceId());
    assertEquals(TENANT_ID, result.getTenantId());
    assertEquals(ENVIRONMENT, result.getEnvironment());
    assertEquals("100", result.getCreatedTimestamp());
    assertEquals("100", result.getModifiedTimestamp());
  }

  @Test
  void testToIntentTemplateDoc_updatesWhenPresent() {
    IntentTemplateDoc existing = IntentTemplateDoc.builder().fieldId("field-1")
        .intentId("intent-1").template("<p>old</p>").createdTimestamp("50").build();
    IntentTemplate intentTemplate =
        IntentTemplate.builder().template("<p>new</p>").userId("user-2").build();

    IntentTemplateDoc result =
        DocConverter.toIntentTemplateDoc(Optional.of(existing), intentTemplate, "200");

    assertEquals("field-1", result.getFieldId());
    assertEquals("<p>new</p>", result.getTemplate());
    assertEquals("user-2", result.getUserId());
    assertEquals("50", result.getCreatedTimestamp());
    assertEquals("200", result.getModifiedTimestamp());
  }

  @Test
  void testToIntentTemplate_presentMapsFields() {
    IntentTemplateDoc doc = IntentTemplateDoc.builder().fieldId("field-1").intentId("intent-1")
        .template("<p>template</p>").businessProcessId(BUSINESS_PROCESS_ID)
        .experienceId(EXPERIENCE_ID).environment(ENVIRONMENT).tenantId(TENANT_ID).build();

    IntentTemplate result = DocConverter.toIntentTemplate(Optional.of(doc));

    assertEquals("field-1", result.getIntentTemplateId());
    assertEquals("intent-1", result.getIntentId());
    assertEquals("template", result.getParsedTemplate());
    assertEquals(BUSINESS_PROCESS_ID, result.getBusinessProcessId());
    assertEquals(EXPERIENCE_ID, result.getExperienceId());
  }

  @Test
  void testToIntentTemplate_absentReturnsEmpty() {
    IntentTemplate result = DocConverter.toIntentTemplate(Optional.empty());

    assertNull(result.getIntentTemplateId());
  }

  @Test
  void testToIntentTemplates_mapsList() {
    List<IntentTemplateDoc> docs = List.of(
        IntentTemplateDoc.builder().fieldId("field-1").intentId("intent-1").template("<p>a</p>")
            .build());

    List<IntentTemplate> result = DocConverter.toIntentTemplates(docs);

    assertEquals(1, result.size());
    assertEquals("field-1", result.get(0).getIntentTemplateId());
    assertEquals("a", result.get(0).getParsedTemplate());
  }

  // ---------- toDispositionDoc / toDisposition / toDispositionLevels ----------

  @Test
  void testToDispositionDoc_createsNewWhenAbsent() {
    TenantContext.setTenantContext(TENANT_ID, ENVIRONMENT);
    Map<Long, String> values = new HashMap<>();
    values.put(1L, "resolved");
    Disposition disposition =
        Disposition.builder().businessProcessId(BUSINESS_PROCESS_ID).name("Outcome")
            .description("desc").values(values).userId("user-1").build();

    DispositionDoc result = DocConverter.toDispositionDoc(Optional.empty(), disposition, "100");

    assertEquals(BUSINESS_PROCESS_ID, result.getBusinessProcessId());
    assertEquals("Outcome", result.getName());
    assertEquals(values, result.getValues());
    assertEquals(TENANT_ID, result.getTenantId());
    assertEquals(ENVIRONMENT, result.getEnvironment());
  }

  @Test
  void testToDispositionDoc_updatesWhenPresent() {
    DispositionDoc existing =
        DispositionDoc.builder().fieldId("field-1").name("Old").createdTimestamp("50").build();
    Disposition disposition = Disposition.builder().name("New").description("new desc").build();

    DispositionDoc result =
        DocConverter.toDispositionDoc(Optional.of(existing), disposition, "200");

    assertEquals("field-1", result.getFieldId());
    assertEquals("New", result.getName());
    assertEquals("new desc", result.getDescription());
    assertEquals("50", result.getCreatedTimestamp());
    assertEquals("200", result.getModifiedTimestamp());
  }

  @Test
  void testToDisposition_presentMapsFields() {
    DispositionDoc doc = DispositionDoc.builder().fieldId("field-1")
        .businessProcessId(BUSINESS_PROCESS_ID).name("Outcome").description("desc")
        .environment(ENVIRONMENT).tenantId(TENANT_ID).build();

    Disposition result = DocConverter.toDisposition(Optional.of(doc));

    assertEquals("field-1", result.getDispositionId());
    assertEquals("Outcome", result.getName());
    assertEquals(BUSINESS_PROCESS_ID, result.getBusinessProcessId());
  }

  @Test
  void testToDisposition_absentReturnsEmpty() {
    Disposition result = DocConverter.toDisposition(Optional.empty());

    assertNull(result.getDispositionId());
  }

  @Test
  void testToDispositionLevels_countsValues() {
    Map<Long, String> values = new HashMap<>();
    values.put(1L, "a");
    values.put(2L, "b");
    DispositionDoc doc = DispositionDoc.builder().fieldId("field-1")
        .businessProcessId(BUSINESS_PROCESS_ID).values(values).environment(ENVIRONMENT)
        .tenantId(TENANT_ID).build();

    List<DispositionLevels> result = DocConverter.toDispositionLevels(List.of(doc));

    assertEquals(1, result.size());
    assertEquals("field-1", result.get(0).getDispositionId());
    assertEquals(2, result.get(0).getCount());
  }

  // ---------- getSummary / getSystemSummary ----------

  @Test
  void testGetSummary_withHeaderSummaryPresent() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    summaryDoc.setHeaderSummary(HeaderSummaryDoc.builder().template("<p>header</p>")
        .timestamp(TimeStampDoc.builder().seconds(500).build()).build());
    summaryDoc.setAgentNotes(AgentNotesDoc.builder().text("note").build());
    Map<String, IntentSummaryDoc> intentSummaries = new LinkedHashMap<>();
    intentSummaries.put("inst-1",
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1")
            .template("<p>summary</p>").timestamp(TimeStampDoc.builder().seconds(100).build())
            .build());
    summaryDoc.setIntentSummaries(intentSummaries);

    SummaryResponse result = DocConverter.getSummary(summaryDoc);

    assertNotNull(result);
    assertEquals(CONVERSATION_ID, result.getConversationId());
    assertEquals("<p>header</p>", result.getHeader().getSummary());
    assertEquals("note", result.getAgentNote());
    assertEquals(1, result.getIntents().size());
    assertEquals("intent-1", result.getIntents().get(0).getIntentId());
  }

  @Test
  void testGetSummary_headerSummaryNullFallsBackToDocTimestamp() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    summaryDoc.setHeaderSummary(null);

    SummaryResponse result = DocConverter.getSummary(summaryDoc);

    assertNotNull(result);
    assertEquals("", result.getHeader().getSummary());
    assertEquals("", result.getAgentNote());
    assertTrue(result.getIntents().isEmpty());
  }

  @Test
  void testGetSystemSummary_mapsSystemIntents() {
    SummaryDoc summaryDoc = baseSummaryDoc();
    Map<String, IntentSummaryDoc> systemIntentSummaries = new LinkedHashMap<>();
    systemIntentSummaries.put("inst-2",
        IntentSummaryDoc.builder().intentId("intent-2").instanceId("inst-2")
            .template("<p>system</p>").timestamp(TimeStampDoc.builder().seconds(100).build())
            .build());
    summaryDoc.setSystemIntentSummaries(systemIntentSummaries);

    SummaryResponse result = DocConverter.getSystemSummary(summaryDoc);

    assertNotNull(result);
    assertEquals(1, result.getSystemIntents().size());
    assertEquals("intent-2", result.getSystemIntents().get(0).getIntentId());
  }

  // ---------- getHeaderSummary / getIntentSummary ----------

  @Test
  void testGetHeaderSummary() {
    TimeStampDoc timestamp = TimeStampDoc.builder().seconds(100).build();

    HeaderResponse result = DocConverter.getHeaderSummary("<p>header</p>", timestamp);

    assertEquals("<p>header</p>", result.getSummary());
    assertNotNull(result.getTimestamp());
  }

  @Test
  void testGetIntentSummary() {
    IntentSummaryDoc intentSummaryDoc =
        IntentSummaryDoc.builder().intentId("intent-1").instanceId("inst-1")
            .template("<p>summary</p>").source("agent").disposition("resolved")
            .timestamp(TimeStampDoc.builder().seconds(100).build()).build();

    IntentResponse result = DocConverter.getIntentSummary(intentSummaryDoc);

    assertEquals("intent-1", result.getIntentId());
    assertEquals("inst-1", result.getInstanceId());
    assertEquals("<p>summary</p>", result.getSummary());
    assertEquals("agent", result.getSource());
    assertEquals("resolved", result.getDisposition());
  }

  // ---------- toGenAISummaryConfigDoc / toGenAISummaryConfig ----------

  @Test
  void testToGenAISummaryConfigDoc_createsNewWhenAbsent() {
    TenantContext.setTenantContext(TENANT_ID, ENVIRONMENT);
    GenAISummaryConfig config = GenAISummaryConfig.builder().experienceId(EXPERIENCE_ID)
        .config("{}").userId("user-1").build();

    GenAISummaryConfigDoc result =
        DocConverter.toGenAISummaryConfigDoc(Optional.empty(), config, "100");

    assertEquals(EXPERIENCE_ID, result.getExperienceId());
    assertEquals("{}", result.getConfig());
    assertEquals(TENANT_ID, result.getTenantId());
    assertEquals(ENVIRONMENT, result.getEnvironment());
    assertEquals("100", result.getCreatedTimestamp());
  }

  @Test
  void testToGenAISummaryConfigDoc_updatesWhenPresent() {
    GenAISummaryConfigDoc existing = GenAISummaryConfigDoc.builder().experienceId(EXPERIENCE_ID)
        .config("{\"old\":true}").createdTimestamp("50").build();
    GenAISummaryConfig config =
        GenAISummaryConfig.builder().config("{\"new\":true}").userId("user-2").build();

    GenAISummaryConfigDoc result =
        DocConverter.toGenAISummaryConfigDoc(Optional.of(existing), config, "200");

    assertEquals("{\"new\":true}", result.getConfig());
    assertEquals("user-2", result.getUserId());
    assertEquals("50", result.getCreatedTimestamp());
    assertEquals("200", result.getModifiedTimestamp());
  }

  @Test
  void testToGenAISummaryConfig_presentMapsFields() {
    GenAISummaryConfigDoc doc = GenAISummaryConfigDoc.builder().experienceId(EXPERIENCE_ID)
        .config("{}").environment(ENVIRONMENT).tenantId(TENANT_ID).build();

    GenAISummaryConfig result = DocConverter.toGenAISummaryConfig(Optional.of(doc));

    assertEquals(EXPERIENCE_ID, result.getExperienceId());
    assertEquals("{}", result.getConfig());
  }

  @Test
  void testToGenAISummaryConfig_absentReturnsEmpty() {
    GenAISummaryConfig result = DocConverter.toGenAISummaryConfig(Optional.empty());

    assertNull(result.getExperienceId());
  }

  // ---------- toAgentStepsDoc ----------

  @Test
  void testToAgentStepsDoc_mapsAgentSteps() {
    ConversationEventEnvelope envelope =
        ConversationEventEnvelope.newBuilder().setConversationId(CONVERSATION_ID)
            .setTenantId(TENANT_ID).setEnvironment(ENVIRONMENT).build();
    List<AgentStepPojo> agentSteps = List.of(
        AgentStepPojo.builder().id("step-1").text("do this").index(0).build());

    AgentStepsDoc result = DocConverter.toAgentStepsDoc(envelope, agentSteps);

    assertEquals(CONVERSATION_ID, result.getConversationId());
    assertEquals(TENANT_ID, result.getTenantId());
    assertEquals(ENVIRONMENT, result.getEnvironment());
    assertEquals(1, result.getAgentSteps().size());
    assertEquals("do this", result.getAgentSteps().get("step-1").getText());
    assertEquals(0, result.getAgentSteps().get("step-1").getOrder());
  }

  private SummaryDoc baseSummaryDoc() {
    return SummaryDoc.builder().conversationId(CONVERSATION_ID).tenantId(TENANT_ID)
        .environment(ENVIRONMENT).businessProcessId(BUSINESS_PROCESS_ID)
        .experienceId(EXPERIENCE_ID).timestamp(TimeStampDoc.builder().seconds(1000).build())
        .genAiSummary(GenAiSummaryDoc.builder().sections(new ArrayList<>()).build())
        .entities(new HashMap<>()).variables(new HashMap<>()).build();
  }
}
