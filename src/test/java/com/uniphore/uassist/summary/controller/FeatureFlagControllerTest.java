package com.uniphore.uassist.summary.controller;

import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.uassist.summary.ApplicationTest;
import com.uniphore.uassist.summary.utils.TestConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY;
import static com.uniphore.uassist.summary.common.Constants.UASSIST_GENAI_OPT_IN_OUT_FF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FeatureFlagControllerTest extends ApplicationTest {

  @MockBean
  private LaunchDarklyHelper ldHelper;

  @Test
  @Order(1)
  void testGetFeatureFlagEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper).isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
          FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/genAISummaryStatus").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting if feature flag is enabled  ", e);
    }
  }

  @Test
  @Order(2)
  void testGetFeatureFlagDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.GENAI_SUMMARY_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/genAISummaryStatus").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting if feature flag is enabled  ", e);
    }
  }

  @Test
  @Order(3)
  void testGetFeatureFlagForDuplicateIntentEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_DUPLICATE_INTENTS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/duplicateIntentEnableFlag").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail(
          "exception occured while getting if feature flag is enabled for duplicate intent  ", e);
    }
  }

  @Test
  @Order(4)
  void testGetFeatureFlagForDuplicateIntentDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_DUPLICATE_INTENTS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/duplicateIntentEnableFlag").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail(
          "exception occured while getting if feature flag is enabled for duplicate intent  ", e);
    }
  }

  @Test
  @Order(5)
  void testGetFeatureFlagForSummaryEditEnhancementsEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EDIT_ENHANCEMENT_FLAG,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/sumEE").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail(
          "exception occured while getting if feature flag for summary edit enhancements is enabled  ",
          e);
    }
  }

  @Test
  @Order(6)
  void testGetFeatureFlagForSummaryEditEnhancementsDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.SUMMARY_EDIT_ENHANCEMENT_FLAG,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/sumEE").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail(
          "exception occured while getting if feature flag for summary edit enhancements is enabled  ",
          e);
    }
  }

  @Test
  @Order(7)
  void testGetFeatureFlagForAgentStepsEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/agent-steps").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(8)
  void testGetFeatureFlagForAgentStepsDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.ENABLE_AGENT_STEPS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/agent-steps").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(9)
  void testGetFeatureFlagForGenAiEnhancementsEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.XCONSOLE_GENAI_SUMMARY_ENHANCEMENTS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/gen-ai-enhancements").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(10)
  void testGetFeatureFlagForGenAiEnhancementsDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.XCONSOLE_GENAI_SUMMARY_ENHANCEMENTS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/gen-ai-enhancements").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(11)
  void testGetFeatureFlagForAutoSubmitEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AUTO_SUBMIT_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/auto-submit-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertNotNull(action.andReturn().getResponse().getContentAsString());
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(12)
  void testGetFeatureFlagForAutoSubmitDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AUTO_SUBMIT_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/auto-submit-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertNotNull(response.get(TestConstant.DATA));
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(13)
  void testGetFeatureFlagForGenAiEntitiesEnhancementsFFEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/feature-flag/gen-ai-entities-enhancements").contentType(
              MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(14)
  void testGetFeatureFlagForGenAiEntitiesEnhancementsFFDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/feature-flag/gen-ai-entities-enhancements").contentType(
              MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(15)
  void testGetFeatureFlagForGQLSummaryUpdateEnhancements() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GQL_SUMMARY_UPDATE_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/uassist-summary-update").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(16)
  void testGetFeatureFlagForGQLSummaryUpdateEnhancementsFFDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GQL_SUMMARY_UPDATE_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/uassist-summary-update").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(17)
  void testGetFeatureFlagForPromiseInGenAISummaryEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_PROMISE_IN_GEN_AI_SUMMARY,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/uassist-promises-under-genai-summary").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(18)
  void testGetFeatureFlagForPromiseInGenAISummaryDisabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_PROMISE_IN_GEN_AI_SUMMARY,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/uassist-promises-under-genai-summary").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(19)
  void testGetFeatureFlagForGenAiEntitiesToLLMEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/uassist-gen-ai-entities-to-llm").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(20)
  void testGetFeatureFlagForGenAiEntitiesToLLMDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GEN_AI_ENTITIES_TO_LLM,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
              get("/v1/feature-flag/uassist-gen-ai-entities-to-llm").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(21)
  void testGetFeatureFlagForAiModelUnderExperienceEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AI_MODEL_UNDER_EXPERIENCE_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/feature-flag/ai-model-under-experience-ff").contentType(
              MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(22)
  void testGetFeatureFlagForAiModelUnderExperienceDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_AI_MODEL_UNDER_EXPERIENCE_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/feature-flag/ai-model-under-experience-ff").contentType(
              MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(23)
  void testGetFeatureFlagForGenAIEditingEnhancementsEnabled() {
    try {
      Mockito.doReturn(true).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GENAI_EDITING_ENHANCEMENTS_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/feature-flag/uassist-genai-editing-enhancements-ff").contentType(
              MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(24)
  void testGetFeatureFlagForGenAIEditingEnhancementsDisabled() {
    try {
      Mockito.doReturn(false).when(this.ldHelper)
          .isFFEnabled(LaunchDarklyConstants.UASSIST_GENAI_EDITING_ENHANCEMENTS_FF,
              FF_CONTEXT_KIND);
      final ResultActions action = this.mockMvc.perform(
          get("/v1/feature-flag/uassist-genai-editing-enhancements-ff").contentType(
              MediaType.APPLICATION_JSON)).andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (final Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for agent steps  ", e);
    }
  }

  @Test
  @Order(25)
  void getFeatureFlagForStreamSummary_ReturnsTrueWhenEnabled() {
    try {
      Mockito.doReturn(true).when(ldHelper).isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-stream-summary-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for summary stream", e);
    }
  }

  @Test
  @Order(26)
  void getFeatureFlagForStreamSummary_ReturnsFalseWhenDisabled() {
    try {
      Mockito.doReturn(false).when(ldHelper)
          .isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-stream-summary-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for summary stream", e);
    }
  }

  @Test
  @Order(27)
  void getFeatureFlagForGenAITargetLanguage_ReturnsTrueWhenEnabled() {
    try {
      Mockito.doReturn(true).when(ldHelper).isFFEnabled(UASSIST_GENAI_TARGET_LANGUAGE_FF, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-genai-target-language-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for genai summary target language", e);
    }
  }

  @Test
  @Order(28)
  void getFeatureFlagForGenAITargetLanguage_ReturnsFalseWhenDisabled() {
    try {
      Mockito.doReturn(false).when(ldHelper).isFFEnabled(UASSIST_GENAI_TARGET_LANGUAGE_FF, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-genai-target-language-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occured while getting feature flag for genai summary target language", e);
    }
  }

  @Test
  @Order(29)
  void getFeatureFlagForGenAIOptInOut_ReturnsTrueWhenEnabled() {
    try {
      Mockito.doReturn(true).when(ldHelper).isFFEnabled(UASSIST_GENAI_OPT_IN_OUT_FF, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-genai-opt-in-out-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting feature flag for genai summary optIn optOut", e);
    }
  }

  @Test
  @Order(30)
  void getFeatureFlagForGenAIOptInOut_ReturnsFalseWhenDisabled() {
    try {
      Mockito.doReturn(false).when(ldHelper).isFFEnabled(UASSIST_GENAI_OPT_IN_OUT_FF, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-genai-opt-in-out-ff").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting feature flag for genai summary optIn optOut", e);
    }
  }

  @Test
  @Order(31)
  void getFeatureFlagForTemplateBasedGenAISummary_ReturnsTrueWhenDisabled() {
    try {
      Mockito.doReturn(true).when(ldHelper).isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-enable-ng-template-based-genai-summary").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("true", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting feature flag for template based genai summary", e);
    }
  }

  @Test
  @Order(32)
  void getFeatureFlagForTemplateBasedGenAISummary_ReturnsFalseWhenDisabled() {
    try {
      Mockito.doReturn(false).when(ldHelper).isFFEnabled(ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND);
      ResultActions action = mockMvc.perform(
              get("/v1/feature-flag/uassist-enable-ng-template-based-genai-summary").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk());
      Map response =
          objectMapper.readValue(action.andReturn().getResponse().getContentAsString(), Map.class);
      assertEquals("false", response.get(TestConstant.DATA));
      assertEquals(200, action.andReturn().getResponse().getStatus(), "Status code 200");
    } catch (Exception e) {
      log.error("exception: ", e);
      Assertions.fail("exception occurred while getting feature flag for template based genai summary", e);
    }
  }

}
