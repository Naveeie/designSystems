package com.uniphore.uassist.summary.integrationtests.cucumberglue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Timestamps;
import com.uniphore.conversations.v1.*;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.telemetry.helper.PlatformTelemetry;
import com.uniphore.uassist.summary.cache.CacheRefreshHelper;
import com.uniphore.uassist.summary.cache.SummaryCacheHelper;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.SummaryUpdateLogDao;
import com.uniphore.uassist.summary.dao.documents.HeaderSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.IntentSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.integrationtests.IntegrationTestHelper;
import com.uniphore.uassist.summary.kafka.MessageConsumer;
import com.uniphore.uassist.summary.utils.TestConstant;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;

import static com.uniphore.uassist.summary.kafka.MessageConsumer.UASSIST_PROCESS_CONVERSATION_EVENTS;
import static com.uniphore.uassist.summary.utils.TestConstant.EXPECTED_PLAINTEXT;
import static com.uniphore.uassist.summary.utils.TestConstant.EXPECTED_TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({TestConstant.PROFILE_NAME, TestConstant.PROFILE_REDIS, TestConstant.PROFILE_MONGO,
    TestConstant.PROFILE_KAFKA})
public class ConversationRuntimeProcessingTest {
  @Autowired
  private CacheOperations<String> headerTemplateCache;
  @Autowired
  private CacheRefreshHelper cacheRefreshHelper;
  @Autowired
  private MessageConsumer messageConsumer;
  @Autowired
  private SummaryRuntimeDao summaryRuntimeDao;
  @Autowired
  private LaunchDarklyHelper ldHelper;
  @Autowired
  private MeterRegistry meterRegistry;
  @Autowired
  private CacheOperations<SummaryDoc> summaryCache;
  @Autowired
  private OpenTelemetry openTelemetry;
  @MockBean
  private PlatformTelemetry platformTelemetry;
  @Autowired
  private IntegrationTestHelper integrationTestHelper;
  @Autowired
  private SummaryUpdateLogDao summaryUpdateLogDao;
  @Autowired
  private SummaryCacheHelper summaryCacheHelper;

  private String conversationId;
  private String externalConversationId;
  private String tenantId;
  private String environment;
  private String bpId;
  private String expId;
  private String entityID;
  private String instanceID;
  private String entityType;
  private String variableInstId;
  private String aiValue;
  private String name;
  private String value;
  private String varId;
  private String text;
  private String intentInstId;
  private String source;
  private String intentId;
  private String entityValue;

  @Given(
      "Conversation id is {string} ExternalConversation id is {string} Tenant id is {string} Environment is {string} BusinessProcess id is {string} and Experience id is {string}")
  public void givenConversationDetails(String convId, String extConvId, String TenantId,
      String Environment, String businessProcessId, String experienceId) {
    conversationId = convId;
    externalConversationId = extConvId;
    tenantId = TenantId;
    environment = Environment;
    bpId = businessProcessId;
    expId = experienceId;
  }

  @When("Conversation Started")
  public void conversationStarted() throws InvalidProtocolBufferException {
    when(ldHelper.isFFEnabled(eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildConversationStartedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("the conversation started event should be present in cache")
  public void verifyConversationStartedInCache() throws InterruptedException {
    Thread.sleep(15000);
    SummaryDoc summaryDocCache = summaryCacheHelper.find(conversationId);
    assertThat(summaryDocCache).isNotNull();
    HeaderSummaryDoc headerSummary = summaryDocCache.getHeaderSummary();
    assertThat(headerSummary.getTemplate()).isEqualTo(EXPECTED_TEMPLATE);
    assertThat(headerSummary.getPlainText()).isEqualTo(EXPECTED_PLAINTEXT);
  }

  @Then("the conversation started event should be stored in database")
  public void verifyConversationStartedInDatabase() {
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    assertThat(doc.getConversationId()).isEqualTo(conversationId);
    assertThat(doc.getTenantId()).isEqualTo(tenantId);
    assertThat(doc.getEnvironment()).isEqualTo(environment);
    assertThat(doc.getBusinessProcessId()).isEqualTo(bpId);
    assertThat(doc.getExperienceId()).isEqualTo(expId);
    HeaderSummaryDoc headerSummary = doc.getHeaderSummary();
    assertThat(headerSummary).isNotNull();
    assertThat(headerSummary.getTemplate()).isEqualTo(EXPECTED_TEMPLATE);
    assertThat(headerSummary.getPlainText()).isEqualTo(EXPECTED_PLAINTEXT);
    assertThat(headerSummary.getTimestamp()).isNotNull();
  }

  @Given(
      "Entity id is {string} Entity instance id is {string} Entity Type is {string} and value is {string}")
  public void entityDetail(String entityID, String instanceID, String entityType, String entity) {
    this.entityID = entityID;
    this.instanceID = instanceID;
    this.entityType = entityType;
    this.entityValue = entity;
  }

  @When("Entity is added")
  public void entityAdded() {
    when(ldHelper.isFFEnabled(eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildEntityAddedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("validating entity added in summary doc from cache")
  public void verifyEntityAddedCache() throws InterruptedException {
    Thread.sleep(5000);
    SummaryDoc summaryDocCache = summaryCacheHelper.find(conversationId);
    assertThat(summaryDocCache).isNotNull();
    SummaryDoc.Slot slot = summaryDocCache.getEntities().get(entityID);
    assertThat(slot).isNotNull();
    assertThat(slot.getValue()).isEqualTo(entityValue);
    assertThat(slot.getId()).isEqualTo(entityID);
    assertThat(slot.getInstanceId()).isEqualTo(instanceID);
    assertThat(slot.isEdited()).isFalse();
  }

  @Then("validating entity added in summary doc from database")
  public void verifyEntityAdded() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    SummaryDoc.Slot slot = doc.getEntities().get(entityID);
    assertThat(slot).isNotNull();
    assertThat(slot.getValue()).isEqualTo(entityValue);
    assertThat(slot.getId()).isEqualTo(entityID);
    assertThat(slot.getInstanceId()).isEqualTo(instanceID);
    assertThat(slot.isEdited()).isFalse();
  }

  @When("Entity is updated")
  public void entityUpdated() {
    when(ldHelper.isFFEnabled(eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildEntityUpdatedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("validating entity updated in summary doc from cache")
  public void verifyEntityUpdatedCache() throws InterruptedException {
    Thread.sleep(5000);
    SummaryDoc summaryDocCache = summaryCacheHelper.find(conversationId);
    assertThat(summaryDocCache).isNotNull();
    SummaryDoc.Slot slot = summaryDocCache.getEntities().get(entityID);
    assertThat(slot).isNotNull();
    assertThat(slot.getValue()).isEqualTo(entityValue);
    assertThat(slot.getId()).isEqualTo(entityID);
    assertThat(slot.getInstanceId()).isEqualTo(instanceID);
    assertThat(slot.isEdited()).isFalse();
  }

  @Then("validating entity updated in summary doc from database")
  public void verifyEntityUpdated() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    SummaryDoc.Slot slot = doc.getEntities().get(entityID);
    assertThat(slot).isNotNull();
    assertThat(slot.getValue()).isEqualTo(entityValue);
    assertThat(slot.getId()).isEqualTo(entityID);
    assertThat(slot.getInstanceId()).isEqualTo(instanceID);
    assertThat(slot.isEdited()).isFalse();
  }

  @Given(
      "VariableInstance id is {string} Ai value is {string} Name is {string} Value is {string} Variable Id is {string}")
  public void variableDetail(String variableInstId, String aiValue, String name, String value,
      String varId) {
    this.variableInstId = variableInstId;
    this.aiValue = aiValue;
    this.name = name;
    this.value = value;
    this.varId = varId;
  }

  @When("Variable is Added")
  public void variableAdded() {
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildVariableAddedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("validating variable added in summary doc from cache")
  public void verifyVariableAddedCache() throws InterruptedException {
    Thread.sleep(5000);
    SummaryDoc summaryDocCache = summaryCacheHelper.find(conversationId);
    assertThat(summaryDocCache).isNotNull();
    SummaryDoc.Slot slot = summaryDocCache.getVariables().get(varId);
    assertThat(slot).isNotNull();
    assertThat(slot.getId()).isEqualTo(varId);
    assertThat(slot.getInstanceId()).isEqualTo(variableInstId);
    assertThat(slot.getValue()).isEqualTo(value);
  }

  @Then("validating variable added in summary doc from database")
  public void verifyVariableAdded() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    SummaryDoc.Slot slot = doc.getVariables().get(varId);
    assertThat(slot).isNotNull();
    assertThat(slot.getId()).isEqualTo(varId);
    assertThat(slot.getInstanceId()).isEqualTo(variableInstId);
    assertThat(slot.getValue()).isEqualTo(value);
  }

  @When("Variable is Updated")
  public void variableUpdated() {
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildVariableUpdatedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("validating variable updated in summary doc from cache")
  public void verifyVariableUpdatedCache() throws InterruptedException {
    Thread.sleep(5000);
    SummaryDoc summaryDocCache = summaryCacheHelper.find(conversationId);
    assertThat(summaryDocCache).isNotNull();
    SummaryDoc.Slot slot = summaryDocCache.getVariables().get(varId);
    assertThat(slot).isNotNull();
    assertThat(slot.getInstanceId()).isEqualTo(variableInstId);
    assertThat(slot.getId()).isEqualTo(varId);
    assertThat(slot.getValue()).isEqualTo(value);
  }

  @Then("validating variable updated in summary doc from database")
  public void verifyVariableUpdated() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    SummaryDoc.Slot slot = doc.getVariables().get(varId);
    assertThat(slot).isNotNull();
    assertThat(slot.getInstanceId()).isEqualTo(variableInstId);
    assertThat(slot.getId()).isEqualTo(varId);
    assertThat(slot.getValue()).isEqualTo(value);
  }

  @Given(
      "Text is {string} Intent InstanceId is {string} Source is {string} and IntentId is {string}")
  public void intentDetail(String text, String IntentInstId, String Source, String IntentId) {
    this.text = text;
    this.intentInstId = IntentInstId;
    this.source = Source;
    this.intentId = IntentId;

  }

  @When("Intent is Added")
  public void intentAdded() {
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildIntentAddedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("validating intent template present in summary doc")
  public void verifyIntentAdded() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    IntentSummaryDoc intentSummaryDoc = doc.getIntentSummaries().get(intentInstId);
    assertThat(intentSummaryDoc).isNotNull();
    assertThat(intentSummaryDoc.getTemplate()).isEqualTo(EXPECTED_TEMPLATE);
    assertThat(intentSummaryDoc.getInstanceId()).isEqualTo(intentInstId);
    assertThat(intentSummaryDoc.getIntentId()).isEqualTo(intentId);
    assertThat(intentSummaryDoc.getSource()).isEqualTo(source);
  }

  @Then("validating entity associated with intent updated in template")
  public void verifyIntentAddedEntity() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    IntentSummaryDoc intentSummaryDoc = doc.getIntentSummaries().get(intentInstId);
    assertThat(intentSummaryDoc.getEntityInstanceIdMap().get(entityID)).isEqualTo(instanceID);
    assertThat(intentSummaryDoc.getTemplate()).isEqualTo(EXPECTED_TEMPLATE);
  }

  @Then("validating entity before deletion in template")
  public void verifyEntityUpdate2() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    System.out.println("summaryDoc" + summaryDoc);
    SummaryDoc doc = summaryDoc.get();
    System.out.println("Doc" + doc);

    Map<String, SummaryDoc.Slot> entityMap = doc.getEntities();
    System.out.println("entityId" + entityID);
    System.out.println("entitymap" + entityMap);

    SummaryDoc.Slot entity = entityMap.get(entityID);
    assertThat(entity.getValue()).isEqualTo(entityValue);
  }

  @Then("validating variable associated with intent updated in template")
  public void verifyIntentAddedVariable() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    IntentSummaryDoc intentSummaryDoc = doc.getIntentSummaries().get(intentInstId);
    assertThat(intentSummaryDoc.getVariableInstanceIdMap().get(varId)).isEqualTo(variableInstId);
    assertThat(intentSummaryDoc.getTemplate()).isEqualTo(EXPECTED_TEMPLATE);
  }

  @Then("validating variable before deletion in template")
  public void verifyVariableUpdate2() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    System.out.println("summaryDoc" + summaryDoc);
    System.out.println("Doc" + doc);

    Map<String, SummaryDoc.Slot> variableMap = doc.getVariables();
    SummaryDoc.Slot variable = variableMap.get(entityID);
    assertThat(variable.getValue()).isEqualTo(value);
  }

  @When("Intent is Deleted")
  public void intentDeleted() {
    when(ldHelper.isFFEnabled(ArgumentMatchers.eq(UASSIST_PROCESS_CONVERSATION_EVENTS),
        ArgumentMatchers.eq(LaunchDarklyConstants.FF_CONTEXT_KIND))).thenReturn(true);
    ConversationEventEnvelope event = buildIntentDeletedEvent();
    integrationTestHelper.publishKafka(event);
  }

  @Then("the intent Deleted event should be processed successfully")
  public void verifyIntentDeleted() throws InterruptedException {
    Thread.sleep(5000);
    Optional<SummaryDoc> summaryDoc = summaryRuntimeDao.getSummaryDoc(conversationId);
    assertThat(summaryDoc).isPresent();
    SummaryDoc doc = summaryDoc.get();
    IntentSummaryDoc intentSummaryDoc = doc.getIntentSummaries().get(intentInstId);
    assertThat(intentSummaryDoc).isNull();
  }

  private ConversationEventEnvelope buildConversationStartedEvent() {
    return ConversationEventEnvelope.newBuilder().setConversationStarted(
            ConversationEventEnvelope.ConversationStarted.newBuilder().setConversation(
                Conversation.newBuilder().setExternalConversationId(externalConversationId)
                    .setConversationId(conversationId).build()).build()).setTenantId(tenantId)
        .setEnvironment(environment).setBusinessProcessId(bpId).setExperienceId(expId)
        .setConversationId(conversationId)
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildIntentAddedEvent() {
    return ConversationEventEnvelope.newBuilder().setIntentsAdded(
            ConversationEventEnvelope.IntentsAdded.newBuilder().addIntents(
                Intent.newBuilder().setIntentType(IntentType.INTENT_TYPE_CALL_REASON)
                    .setDetectedBy(source).setLabel(intentId).setText(text).setInstanceId(intentInstId)
                    .build()).build()).setTenantId(tenantId).setEnvironment(environment)
        .setBusinessProcessId(bpId).setExperienceId(expId).setConversationId(conversationId)
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildIntentDeletedEvent() {
    return ConversationEventEnvelope.newBuilder().setIntentDeleted(
            ConversationEventEnvelope.IntentDeleted.newBuilder().setIntent(
                Intent.newBuilder().setIntentType(IntentType.INTENT_TYPE_CALL_REASON)
                    .setDetectedBy(source).setLabel(intentId).setText(text).setInstanceId(intentInstId)
                    .build()).build()).setTenantId(tenantId).setEnvironment(environment)
        .setBusinessProcessId(bpId).setExperienceId(expId).setConversationId(conversationId)
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildEntityAddedEvent() {
    return ConversationEventEnvelope.newBuilder().setExternalConversationId(externalConversationId)
        .setEnvironment(environment).setTenantId(tenantId).setBusinessProcessId(bpId)
        .setExperienceId(expId).setConversationId(conversationId).setEntityAdded(
            ConversationEventEnvelope.EntityAdded.newBuilder().setEntity(
                Entity.newBuilder().setValue(entityValue).setEntityId(entityID).setType(entityType)
                    .setEntityInstanceId(instanceID).build()).build())
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildEntityUpdatedEvent() {
    return ConversationEventEnvelope.newBuilder().setExternalConversationId(externalConversationId)
        .setEnvironment(environment).setTenantId(tenantId).setBusinessProcessId(bpId)
        .setExperienceId(expId).setConversationId(conversationId).setEntityUpdated(
            ConversationEventEnvelope.EntityUpdated.newBuilder().setEntity(
                Entity.newBuilder().setValue(entityValue).setEntityId(entityID)
                    .setEntityInstanceId(instanceID).setType(entityType)).build())
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildVariableAddedEvent() {
    return ConversationEventEnvelope.newBuilder().setExternalConversationId(externalConversationId)
        .setEnvironment(environment).setTenantId(tenantId).setBusinessProcessId(bpId)
        .setExperienceId(expId).setConversationId(conversationId).setVariablesAdded(
            ConversationEventEnvelope.VariablesAdded.newBuilder().addVariables(
                Variable.newBuilder().setVariableId(varId).setVariableInstanceId(variableInstId)
                    .setAiValue(aiValue).setName(name).setValue(value).build()))
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

  private ConversationEventEnvelope buildVariableUpdatedEvent() {
    return ConversationEventEnvelope.newBuilder().setExternalConversationId(externalConversationId)
        .setEnvironment(environment).setTenantId(tenantId).setBusinessProcessId(bpId)
        .setExperienceId(expId).setConversationId(conversationId).setVariablesUpdated(
            ConversationEventEnvelope.VariablesUpdated.newBuilder().addVariables(
                Variable.newBuilder().setVariableId(varId).setVariableInstanceId(variableInstId)
                    .setAiValue(aiValue).setName(name).setValue(value).build()))
        .setTimestamp(Timestamps.fromMillis(System.currentTimeMillis())).build();
  }

}
