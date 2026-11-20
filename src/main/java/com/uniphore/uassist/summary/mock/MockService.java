package com.uniphore.uassist.summary.mock;

import com.uniphore.conversations.v1.*;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.uassist.summary.cache.CacheRefreshHelper;
import com.uniphore.uassist.summary.cache.HeaderTemplateCacheHelper;
import com.uniphore.uassist.summary.cache.IntentTemplateCacheHelper;
import com.uniphore.uassist.summary.common.Commons;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.model.AgentRequestPayload;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.SummaryHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MockService {

    @Autowired
    private SummaryRuntimeService messageConsumer;
    @Autowired
    private HeaderTemplateCacheHelper headerTemplateCacheHelper;
    @Autowired
    private IntentTemplateCacheHelper intentTemplateCacheHelper;

    @Autowired
    private SummaryRuntimeService summaryRuntimeService;

    @Autowired
    private CacheRefreshHelper cacheRefreshHelper;

    @Autowired
    private SummaryHelper summaryHelper;

    public void mockIntentAddedEvent(Map<String, String> data) {
        Intent intent = Intent.newBuilder().setLabel(data.get("intentLabel")).setText(data.get("text"))
                .setParticipantId(data.get("participantId"))
                .setIntentType(IntentType.INTENT_TYPE_CALL_REASON).setInstanceId(data.get("instanceId"))
                .setTimestamp(DateUtil.getCurrentTimestamp()).setDetectedBy(data.get("detectedBy")).build();

        List<Intent> intents = new ArrayList<>();
        intents.add(intent);

        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                        .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                        .setIntentsAdded(
                                ConversationEventEnvelope.IntentsAdded.newBuilder().addAllIntents(intents).build())
                        .setBusinessProcessId(data.get("businessProcessId"))
                        .setExperienceId(data.get("experienceId")).setTimestamp(DateUtil.getCurrentTimestamp())
                        .build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockIntentAddedEventIntentTypeAgentSteps(Map<String, String> data) {
        Intent intent = Intent.newBuilder()
            .setLabel("12").setText(data.get("text"))
            .setParticipantId(data.get("participantId"))
            .setIntentType(IntentType.INTENT_TYPE_AGENT_STEPS).setInstanceId(data.get("instanceId"))
            .setTimestamp(DateUtil.getCurrentTimestamp()).setDetectedBy(data.get("detectedBy")).build();

        List<Intent> intents = new ArrayList<>();
        intents.add(intent);

        ConversationEventEnvelope conversationEventEnvelope =
            ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                .setIntentsAdded(
                    ConversationEventEnvelope.IntentsAdded.newBuilder().addAllIntents(intents).build())
                .setBusinessProcessId(data.get("businessProcessId"))
                .setExperienceId(data.get("experienceId")).setTimestamp(DateUtil.getCurrentTimestamp())
                .build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
            data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockIntentAddedEventNoValues() {
        Intent intent = Intent.newBuilder().setLabel("intentLabel").setText("text")
                .setParticipantId("participantId").setIntentType(IntentType.INTENT_TYPE_CALL_REASON)
                .setInstanceId("instanceId").setTimestamp(DateUtil.getCurrentTimestamp())
                .setDetectedBy("detectedBy").build();
        List<Intent> intents = new ArrayList<>();
        intents.add(intent);
        ConversationEventEnvelope conversationEventEnvelope = ConversationEventEnvelope.newBuilder()
                .setIntentsAdded(
                        ConversationEventEnvelope.IntentsAdded.newBuilder().addAllIntents(intents).build())
                .build();
        ContextSetter.setContext("tenantId", "environment", "conversationId");
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockEntityAddedEvent(Map<String, String> data) {
        Entity entity =
                Entity.newBuilder().setEntityId(data.get("entityId")).setValue(data.get("value"))
                        .setParticipantId("1").setDetectedBy(data.get("detectedBy"))
                        .setEntityInstanceId(data.get("instanceId")).setType("entity")
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                        .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                        .setEntityAdded(
                                ConversationEventEnvelope.EntityAdded.newBuilder().setEntity(entity).build())
                        .setExperienceId(data.get("experienceId"))
                        .setBusinessProcessId(data.get("businessProcessId"))
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockVariableAddedEvent(Map<String, String> data) {
        Variable variable = Variable.newBuilder().setVariableId(data.get("variableId"))
                .setVariableInstanceId(data.get("instanceId")).setName(data.get("name"))
                .setValue(data.get("value")).setAiValue(data.get("aiValue"))
                .setSourceType(VariableSourceType.VARIABLE_SOURCE_GEN_AI_SUMMARY)
                .setSourceId(data.get("sourceId")).setTimestamp(DateUtil.getCurrentTimestamp())
                .setVariableInstanceId(data.get("instanceId")).build();

        List<Variable> variables = new ArrayList<>();
        variables.add(variable);
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                        .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                        .setVariablesAdded(
                                ConversationEventEnvelope.VariablesAdded.newBuilder().addAllVariables(variables)
                                        .build()).setExperienceId(data.get("experienceId"))
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockEntityUpdatedEvent(Map<String, String> data) {
        Entity entity =
                Entity.newBuilder().setEntityId(data.get("entityId")).setValue(data.get("value"))
                        .setParticipantId("1").setDetectedBy(data.get("detectedBy"))
                        .setEntityInstanceId(data.get("instanceId")).setType("entity")
                        .setEditSourceType(EditSourceType.EDIT_SOURCE_TYPE_INTENT_CALL_REASON)
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                        .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                        .setEntityUpdated(
                                ConversationEventEnvelope.EntityUpdated.newBuilder().setEntity(entity).build())
                        .setExperienceId(data.get("experienceId"))
                        .setBusinessProcessId(data.get("businessProcessId"))
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockVariableUpdatedEvent(Map<String, String> data) {
        Variable variable = Variable.newBuilder().setVariableId(data.get("variableId"))
                .setVariableInstanceId(data.get("instanceId")).setName(data.get("name"))
                .setValue(data.get("value")).setAiValue(data.get("aiValue"))
                .setSourceType(VariableSourceType.VARIABLE_SOURCE_UNKNOWN).setSourceId(data.get("sourceId"))
                .setTimestamp(DateUtil.getCurrentTimestamp()).setVariableInstanceId(data.get("instanceId"))
                .build();

        List<Variable> variables = new ArrayList<>();
        variables.add(variable);
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId")).setBusinessProcessId("bp123")
                        .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                        .setVariablesUpdated(
                                ConversationEventEnvelope.VariablesUpdated.newBuilder().addAllVariables(variables)
                                        .build()).setExperienceId(data.get("experienceId"))
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockIntentDeletedEvent(Map<String, String> data) {
        Intent intent = Intent.newBuilder().setLabel(data.get("intentLabel")).setText(data.get("text"))
                .setParticipantId(data.get("participantId"))
                .setIntentType(IntentType.INTENT_TYPE_CALL_REASON).setInstanceId(data.get("instanceId"))
                .setTimestamp(DateUtil.getCurrentTimestamp()).setDetectedBy(data.get("detectedBy")).build();

        List<Intent> intents = new ArrayList<>();
        intents.add(intent);
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                        .setEnvironment(data.get("environment")).setConversationId(data.get("conversationId"))
                        .setIntentDeleted(
                                ConversationEventEnvelope.IntentDeleted.newBuilder().setIntent(intent).build())
                        .setBusinessProcessId(data.get("businessProcessId"))
                        .setExperienceId(data.get("experienceId")).setTimestamp(DateUtil.getCurrentTimestamp())
                        .build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockFlowEndedEvent(Map<String, String> data) {
        Intent intent =
                Intent.newBuilder().setInstanceId("instanceId").setDetectedBy("USER").setLabel("label")
                        .setParticipantId("Participant1").setText("text")
                        .setTimestamp(DateUtil.getCurrentTimestamp())
                        .setIntentType(IntentType.INTENT_TYPE_CALL_REASON).build();

        List<Intent> intents = new ArrayList<>();
        intents.add(intent);
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
                        .setConversationId("conversationId").setFlowEnded(
                                (ConversationEventEnvelope.FlowEnded.newBuilder().setFlowInstanceId("instanceId")
                                        .build())).setBusinessProcessId("businessProcessId").setExperienceId("experienceId")
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockConversationStartedEvent(Map<String, String> data) {
        List<Participant> participants = new ArrayList<>();
        participants.add(Participant.newBuilder().setParticipantId(data.get("agentId")).setType("agent")
                .setCtiAgentId(data.get("ctiAgentId")).build());
        participants.add(
                Participant.newBuilder().setParticipantId(data.get("customerId")).setType("customer")
                        .setCtiAgentId(data.get("ctiAgentId")).build());
        State state = State.newBuilder().setState(ConversationState.CONVERSATION_STATE_ACTIVE)
                .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        List<State> states = new ArrayList<>();
        states.add(state);
        Conversation conversation = Conversation.newBuilder().setTenantId(data.get("tenantId"))
                .setConversationId(data.get("conversationId")).addAllStates(states)
                .setEnvironment(data.get("environment")).setBusinessProcessId(data.get("businessProcessId"))
                .setLanguage(data.get("language")).addAllParticipants(participants).build();

        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId(data.get("tenantId"))
                        .setEnvironment(data.get("environment"))
                        .setBusinessProcessId(data.get("businessProcessId"))
                        .setConversationId(data.get("conversationId"))
                        .setConversationStarted(
                                ConversationEventEnvelope.ConversationStarted.newBuilder().setConversation(conversation)
                                        .build()).setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext(data.get("tenantId"), data.get("environment"),
                data.get("conversationId")
        );
        this.summaryRuntimeService.handleConversationEvent(
                conversationEventEnvelope,
                DateUtil.TIME.millis()
        );
    }

    public void mockSaveAgentNotes() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .conversationId("conversationId").disposition("disposition")
                        .businessProcessId("businessProcessId").entityEditPayloads(entityEditPayloads)
                        .genAiSummaryDoc(genAiSummaryDoc).headerSummary("header").intentSummary("intent")
                        .instanceId("123").source("agent").intentId("12").build();
        this.summaryRuntimeService.saveAgentNotes(agentRequestPayload);
    }

    public void mockHandleEntityUpdatedEvent() {
        Entity entity =
                Entity.newBuilder().setEntityId("entityId").setValue("value").setParticipantId("1")
                        .setDetectedBy("detectedBy").setEntityInstanceId("instanceId").setType("entity")
                        .setEditSourceType(EditSourceType.EDIT_SOURCE_TYPE_HEADER_SUMMARY)
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
                        .setConversationId("conversationId").setEntityUpdated(
                                ConversationEventEnvelope.EntityUpdated.newBuilder().setEntity(entity).build())
                        .setExperienceId("experienceId").setBusinessProcessId("businessProcessId")
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext("tenantId", "environment", "conversationId");
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());
    }

    public void mockHandleEntityUpdatedEventWithoutSourceType() {
        Entity entity =
                Entity.newBuilder().setEntityId("entityId").setValue("value").setParticipantId("1")
                        .setDetectedBy("detectedBy").setEntityInstanceId("instanceId").setType("entity")
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ConversationEventEnvelope conversationEventEnvelope =
                ConversationEventEnvelope.newBuilder().setTenantId("tenantId").setEnvironment("dev")
                        .setConversationId("conversationId").setEntityUpdated(
                                ConversationEventEnvelope.EntityUpdated.newBuilder().setEntity(entity).build())
                        .setExperienceId("experienceId").setBusinessProcessId("businessProcessId")
                        .setTimestamp(DateUtil.getCurrentTimestamp()).build();
        ContextSetter.setContext("tenantId", "environment", "conversationId");
        this.messageConsumer.handleConversationEvent(conversationEventEnvelope, DateUtil.TIME.millis());

    }

    public void mockSaveAgentEditsWithHeaderSummary() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .conversationId("conversationId").disposition("disposition")
                        .businessProcessId("businessProcessId").entityEditPayloads(entityEditPayloads)
                        .headerSummary("header").instanceId("123").source("agent").intentId("12").build();
        this.summaryRuntimeService.saveAgentEdits(agentRequestPayload);
    }

    public void mockSaveAgentEditsWithIntentSummary() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
        sectionList.add(GenAiSummaryDoc.Section.builder().text("sectionText").id("12").build());
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .conversationId("conversationId").disposition("disposition")
                        .businessProcessId("businessProcessId").entityEditPayloads(entityEditPayloads)
                        .intentSummary("intent").instanceId("123").source("agent").intentId("12").build();
        this.summaryRuntimeService.saveAgentEdits(agentRequestPayload);
    }

    public void mockSaveAgentEditsWithGenAiSummary() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
        sectionList.add(GenAiSummaryDoc.Section.builder().text("sectionText").id("12").build());
        GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .headerSummary("header").genAiSummaryDoc(genAiSummaryDoc)
                        .conversationId("conversationId").businessProcessId("businessProcessId")
                        .entityEditPayloads(entityEditPayloads).genAiSummaryDoc(genAiSummaryDoc)
                        .headerSummary("123").source("agent").build();
        this.summaryRuntimeService.saveAgentEdits(agentRequestPayload);
    }

    public void mockSaveAgentEditsWithoutGenAiSummary() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
        sectionList.add(GenAiSummaryDoc.Section.builder().text("sectionText").id("12").build());
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .conversationId("conversationId").businessProcessId("businessProcessId")
                        .entityEditPayloads(entityEditPayloads).source("agent").build();
        this.summaryRuntimeService.saveAgentEdits(agentRequestPayload);
    }

    public void mockSaveAgentEditsWithoutConvId() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
        sectionList.add(GenAiSummaryDoc.Section.builder().text("sectionText").id("12").build());
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes").conversationId("")
                        .businessProcessId("businessProcessId").extConversationId("extConvId")
                        .entityEditPayloads(entityEditPayloads).source("agent").build();
        this.summaryRuntimeService.saveAgentEdits(agentRequestPayload);
    }

    public void mockProcessAgentUpdatesWithAddIntent(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.INTENT_ADD).agentRequestPayload(agentRequestPayload)
                        .environment("dev").timestamp(System.currentTimeMillis()).tenantId("tenantId")
                        .conversationId("123").build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithGenAiSummaryEdit(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.GEN_AI_SUMMARY_EDIT)
                        .agentRequestPayload(agentRequestPayload).environment("dev")
                        .timestamp(System.currentTimeMillis()).tenantId("tenantId").conversationId("123")
                        .build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithHeaderEdit(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.HEADER_EDIT).agentRequestPayload(agentRequestPayload)
                        .environment("dev").timestamp(System.currentTimeMillis()).tenantId("tenantId")
                        .conversationId("123").build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithIntentEdit(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.INTENT_EDIT).agentRequestPayload(agentRequestPayload)
                        .environment("dev").timestamp(System.currentTimeMillis()).tenantId("tenantId")
                        .conversationId("123").build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithAgentNotes(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.AGENT_NOTES).agentRequestPayload(agentRequestPayload)
                        .environment("dev").timestamp(System.currentTimeMillis()).tenantId("tenantId")
                        .conversationId("123").build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithIntentDelete(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.INTENT_DELETE).agentRequestPayload(agentRequestPayload)
                        .environment("dev").timestamp(System.currentTimeMillis()).tenantId("tenantId")
                        .conversationId("123").build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithAddAgentStep(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.ADD_AGENT_STEP)
                        .agentRequestPayload(agentRequestPayload).environment("dev")
                        .timestamp(System.currentTimeMillis()).tenantId("tenantId").conversationId("123")
                        .build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithDeleteAgentStep(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.DELETE_AGENT_STEP)
                        .agentRequestPayload(agentRequestPayload).environment("dev")
                        .timestamp(System.currentTimeMillis()).tenantId("tenantId").conversationId("123")
                        .build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithUpdateAgentStep(
            AgentRequestPayload agentRequestPayload,
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest.AgentStepUpdate agentStepUpdate =
                AgentUpdateRequest.AgentStepUpdate.builder().id("1234").value("agentStep").build();
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentStepUpdate(agentStepUpdate)
                        .agentAction(Commons.AgentAction.UPDATE_AGENT_STEP)
                        .agentRequestPayload(agentRequestPayload).environment("dev")
                        .timestamp(System.currentTimeMillis()).tenantId("tenantId").conversationId("123")
                        .build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockProcessAgentUpdatesWithInvalid(
            List<SummaryUpdateEventLog.EventData> eventDataList
    ) {
        AgentUpdateRequest agentUpdateRequest =
                AgentUpdateRequest.builder().agentRequestPayload(AgentRequestPayload.builder().conversationId("123").build())
                .agentAction(Commons.AgentAction.INVALID).environment("dev")
                        .timestamp(System.currentTimeMillis()).tenantId("tenantId").conversationId("123")
                        .build();
        this.summaryRuntimeService.processAgentUpdates(agentUpdateRequest, eventDataList);
    }

    public void mockAddIntent() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
        GenAiSummaryDoc.Section sectionDocUnformatted =
                GenAiSummaryDoc.Section.builder().id("summary").text("String").build();
        sectionList.add(sectionDocUnformatted);
        GenAiSummaryDoc.Section.builder().id("123").text("Section").build();
        GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .conversationId("conversationId").disposition("disposition")
                        .businessProcessId("businessProcessId").entityEditPayloads(entityEditPayloads)
                        .genAiSummaryDoc(genAiSummaryDoc).headerSummary("header").intentSummary("intent")
                        .instanceId("123").source("agent").intentId("12").build();
        List<String> effectedIntents = new ArrayList<>();
        effectedIntents.add("intent1");
        effectedIntents.add("intent2");
        SummaryUpdateEventLog.EventData eventData =
                SummaryUpdateEventLog.EventData.builder().eventId("asd").value("eventData")
                        .detectedBy("agent").headerEffected(true).instanceId("1")
                        .effectedIntents(effectedIntents).build();
        List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
        eventDataList.add(eventData);

        this.summaryRuntimeService.addIntent(agentRequestPayload);
    }

    public void mockDeleteIntent() {
        List<AgentRequestPayload.EntityEditPayload> entityEditPayloads = new ArrayList<>();
        List<GenAiSummaryDoc.Section> sectionList = new ArrayList<>();
        GenAiSummaryDoc.Section sectionDocUnformatted =
                GenAiSummaryDoc.Section.builder().id("summary").text("String").build();
        sectionList.add(sectionDocUnformatted);
        GenAiSummaryDoc.Section.builder().id("123").text("Section").build();
        GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(sectionList).build();
        entityEditPayloads.add(
                AgentRequestPayload.EntityEditPayload.builder().isEntity(true).editedComponentId("123")
                        .editedValue("agentNotes").build());
        AgentRequestPayload agentRequestPayload =
                AgentRequestPayload.builder().agentId("agentId").agentNotes("agentNotes")
                        .conversationId("conversationId").disposition("disposition")
                        .businessProcessId("businessProcessId").entityEditPayloads(entityEditPayloads)
                        .genAiSummaryDoc(genAiSummaryDoc).headerSummary("header").intentSummary("intent")
                        .instanceId("123").source("agent").intentId("12").build();
        List<String> effectedIntents = new ArrayList<>();
        effectedIntents.add("intent1");
        effectedIntents.add("intent2");
        SummaryUpdateEventLog.EventData eventData =
                SummaryUpdateEventLog.EventData.builder().eventId("asd").value("eventdata")
                        .detectedBy("agent").headerEffected(true).instanceId("1")
                        .effectedIntents(effectedIntents).build();
        List<SummaryUpdateEventLog.EventData> eventDataList = new ArrayList<>();
        eventDataList.add(eventData);
        this.summaryRuntimeService.deleteIntent(agentRequestPayload);
    }

}
