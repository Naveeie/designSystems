package com.uniphore.uassist.summary.utils;

public class TestConstant {

  public static final String CLASSPATH_JSON_CONFIG = "src/test/resources/json/runtime/";
  public static final String PROFILE = "spring.profiles.include:test";
  public static final String PROFILE_NAME = "test";
  public static final String PROFILE_REDIS = "redis";
  public static final String PROFILE_MONGO = "mongo";
  public static final String PROFILE_KAFKA = "kafka";

  public static final String CONTAINER_LABEL_VALUE = "test-container";

  public static final String REDIS_DOCKER_IMAGE = "redis:6-alpine";
  public static final String REDIS_CONTAINER_LABEL_KEY = "ri-redis";
  public static final String REDIS_HOST_PORT = "spring.data.redis.cluster.nodes";
  public static final String MONGO_CONTAINER_LABEL_KEY = "ri-mongo";
  public static final String MONGO_DOCKER_IMAGE = "mongo:3.2.4";

  public static final String KAFKA_DOCKER_IMAGE = "confluentinc/cp-kafka:5.4.3";
  public static final String KAFKA_CONTAINER_LABEL_KEY = "ri-kafka";
  public static final String KAFKA_BROKER_KEY = "spring.kafka.bootstrap-servers";
  public static final String USERNAME = "dev";
  public static final String EXPERIENCE_ID = "52b81e03-69a1-4d43-8759-726b6ac55f19";
  public static final String TENANT_ID = "3e37631c-78ac-4137-8415-72dcd90b19fd";
  public static final String TENANT_CACHE_KEY = "uniphore.conf.uassist.tenants";
  public static final String CONVERSATION_ID = "cfr3sh3hjl1dk9u130hl";
  public static final String BUSINESS_PROCESS = "7341466421122183431708590568104";

  public static final String SUMMARY_DOC_RESPONSE_JSON = "summary-doc.json";
  public static final String CONVERSATION_1_START_EVENT = "conversation1StartEvent.json";
  public static final String HEADER_TEMPLATE_BP1 = "header-template-BP1.json";
  public static final String INTENT_TEMPLATE_BP1 = "intent-template-BP1.json";
  public static final String ENTITY1_BP1 = "entity1-BP1.json";
  public static final String ENTITY1_UPDATED_BP1 = "entity1-updated-BP1.json";
  public static final String INTENT1_BP1 = "intent1-BP1.json";
  public static final String VARIABLE1_BP1 = "variable1-BP1.json";
  public static final String VARIABLE1_UPDATED_BP1 = "variable1-updated-BP1.json";
  public static final String CONVERSATION_2_START_EVENT = "conversation2StartEvent.json";
  public static final String GEN_AI_CONFIG_JSON = "gen-ai-config.json";
  public static final String GEN_AI_CONFIG_NO_FORMAT = "gen-ai-config-no-format.json";
  public static final String GEN_AI_SUMMARY_CONFIG_DOC = "genAISummaryConfigDoc.json";
  public static final String HEADER_TEMPLATE_BP2 = "header-template-BP2.json";
  public static final String INTENT_TEMPLATE_BP2 = "intent-template-BP2.json";
  public static final String ENTITY1_BP2 = "entity1-BP2.json";
  public static final String ENTITY1_UPDATED_BP2 = "entity1-updated-BP2.json";
  public static final String ENTITY1_UPDATED_AGAIN_BP2 = "entity1-updated-again-BP2.json";
  public static final String INTENT1_BP2 = "intent1-BP2.json";
  public static final String INTENT2_BP2 = "intent2-BP2.json";
  public static final String INTENT_ADD = "intent-add.json";
  public static final String VARIABLE1_BP2 = "variable1-BP2.json";
  public static final String VARIABLE1_UPDATED_BP2 = "variable1-updated-BP2.json";
  public static final String VARIABLE1_UPDATED_AGAIN_BP2 = "variable1-updated-again-BP2.json";
  public static final String AGENT_NOTES_REQUEST_JSON = "agent-notes.json";
  public static final String AGENT_NOTES_NO_VALUES_REQUEST_JSON = "agent-notes-no-values.json";
  public static final String GEN_AI_CACHE_JSON = "gen-ai-cache.json";
  public static final String AGENT_INTENT_EDITS_REQUEST_JSON = "agent-intent-edits.json";
  public static final String AGENT_INTENT_EDITS_NO_VALUES_REQUEST_JSON =
      "agent-intent-edits-no-values.json";
  public static final String AGENT_REQUEST_PAYLOAD = "agentRequestPayload.json";
  public static final String AGENT_HEADER_EDITS_REQUEST_JSON = "agent-header-edits.json";
  public static final String AGENT_DISPOSITION_EDITS_REQUEST_JSON = "agent-disposition-edits.json";
  public static final String INTENT_ADD_REQUEST_JSON = "intent-add.json";
  public static final String DATA = "data";
  public static final String EXPECTED_PLAINTEXT = "customer name : TBD";
  public static final String EXPECTED_TEMPLATE ="<p color=\"black\">customer name : <span entity-id=\"1\" class=\"uniphore-entity-name\">TBD</span></p>";



  public static final String STATUS_200 = "Status code 200";
  public static final String STATUS_400 = "Status code 400";
  public static final String STATUS_404 = "Status code 404";
  public static final String STATUS_500 = "Status code 500";

  private TestConstant() {}

}
