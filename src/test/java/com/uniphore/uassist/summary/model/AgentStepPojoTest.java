package com.uniphore.uassist.summary.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentStepPojoTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void testBuilderAndGettersSetters() {
    AgentStepPojo pojo = AgentStepPojo.builder().id("step1").text("Sample text").index(1).build();
    assertThat(pojo.getId()).isEqualTo("step1");
    assertThat(pojo.getText()).isEqualTo("Sample text");
    assertThat(pojo.getIndex()).isEqualTo(1);
  }

  @Test
  void testBuilderAndToBuilder() {
    AgentStepPojo originalPojo =
        AgentStepPojo.builder().id("step1").text("Sample text").index(1).build();
    AgentStepPojo  modifiedPojo = originalPojo.toBuilder().text("Updated text").build();
    assertThat(originalPojo.getText()).isEqualTo("Sample text");
    assertThat(modifiedPojo.getText()).isEqualTo("Updated text");
  }

  @Test
  void testJsonSerialization() throws Exception {
    AgentStepPojo pojo = AgentStepPojo.builder().id("step1").text("Sample text").index(1).build();
    String json = objectMapper.writeValueAsString(pojo);
    assertThat(json).contains("\"id\":\"step1\"");
    assertThat(json).contains("\"text\":\"Sample text\"");
    assertThat(json).contains("\"index\":1");
  }

  @Test
  void testJsonDeserialization() throws Exception {
    String json = "{" + "\"id\":\"step1\"," + "\"text\":\"Sample text\"," + "\"index\":1" + "}";
    AgentStepPojo pojo = objectMapper.readValue(json, AgentStepPojo.class);
    assertThat(pojo.getId()).isEqualTo("step1");
    assertThat(pojo.getText()).isEqualTo("Sample text");
    assertThat(pojo.getIndex()).isEqualTo(1);
  }

  @Test
  void testJsonDeserializationWithNullValues() throws Exception {
    String json = "{}";
    AgentStepPojo pojo = objectMapper.readValue(json, AgentStepPojo.class);
    assertThat(pojo.getId()).isNull();
    assertThat(pojo.getText()).isNull();
    assertThat(pojo.getIndex()).isZero();
  }
}
