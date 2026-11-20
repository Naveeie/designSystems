package com.uniphore.uassist.summary.dao.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentStepsDocTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
  }

  @Test
  void testBuilderAndGettersSetters() {
    AgentStepsDoc.AgentStep agentStep =
        AgentStepsDoc.AgentStep.builder().id("step1").order(1).text("Step text")
            .detectedBy("System").build();
    Map<String, AgentStepsDoc.AgentStep> agentStepsMap = new HashMap<>();
    agentStepsMap.put("step1", agentStep);
    AgentStepsDoc agentStepsDoc =
        AgentStepsDoc.builder().conversationId("conv123").environment("dev").tenantId("tenant1")
            .agentSteps(agentStepsMap).build();
    assertThat(agentStepsDoc.getConversationId()).isEqualTo("conv123");
    assertThat(agentStepsDoc.getEnvironment()).isEqualTo("dev");
    assertThat(agentStepsDoc.getTenantId()).isEqualTo("tenant1");
    assertThat(agentStepsDoc.getAgentSteps()).containsEntry("step1", agentStep);
    assertThat(agentStepsDoc.getAgentSteps().get("step1").getId()).isEqualTo("step1");
    assertThat(agentStepsDoc.getAgentSteps().get("step1").getOrder()).isEqualTo(1);
    assertThat(agentStepsDoc.getAgentSteps().get("step1").getText()).isEqualTo("Step text");
    assertThat(agentStepsDoc.getAgentSteps().get("step1").getDetectedBy()).isEqualTo("System");
  }

  @Test
  void testSerialization() throws IOException {
    AgentStepsDoc.AgentStep agentStep =
        AgentStepsDoc.AgentStep.builder().id("step1").order(1).text("Step text")
            .detectedBy("System").build();
    Map<String, AgentStepsDoc.AgentStep> agentStepsMap = new HashMap<>();
    agentStepsMap.put("step1", agentStep);
    AgentStepsDoc agentStepsDoc =
        AgentStepsDoc.builder().conversationId("conv123").environment("dev").tenantId("tenant1")
            .agentSteps(agentStepsMap).build();
    String json = objectMapper.writeValueAsString(agentStepsDoc);
    AgentStepsDoc deserializedDoc = objectMapper.readValue(json, AgentStepsDoc.class);
    assertThat(deserializedDoc.getConversationId()).isEqualTo("conv123");
    assertThat(deserializedDoc.getEnvironment()).isEqualTo("dev");
    assertThat(deserializedDoc.getTenantId()).isEqualTo("tenant1");
    assertThat(deserializedDoc.getAgentSteps()).containsEntry("step1", agentStep);
    assertThat(deserializedDoc.getAgentSteps().get("step1").getId()).isEqualTo("step1");
    assertThat(deserializedDoc.getAgentSteps().get("step1").getOrder()).isEqualTo(1);
    assertThat(deserializedDoc.getAgentSteps().get("step1").getText()).isEqualTo("Step text");
    assertThat(deserializedDoc.getAgentSteps().get("step1").getDetectedBy()).isEqualTo("System");
  }

  @Test
  void testNestedBuilder() {
    AgentStepsDoc.AgentStep agentStep =
        AgentStepsDoc.AgentStep.builder().id("step1").order(1).text("Step text")
            .detectedBy("System").build();
    AgentStepsDoc.AgentStep modifiedAgentStep =
        agentStep.toBuilder().text("Modified Step text").build();
    assertThat(agentStep.getText()).isEqualTo("Step text");
    assertThat(modifiedAgentStep.getText()).isEqualTo("Modified Step text");
  }
}
