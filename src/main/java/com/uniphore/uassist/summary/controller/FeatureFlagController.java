package com.uniphore.uassist.summary.controller;

import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.uassist.summary.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.UASSIST_GENAI_OPT_IN_OUT_FF;

@RestController
@Slf4j
@RequestMapping("/v1/feature-flag")
public class FeatureFlagController {
  @Autowired
  private LaunchDarklyHelper ldHelper;
  @GetMapping("/genAISummaryStatus")
  public ResponseEntity<Map<String, Object>> getFeatureFlagEnabled() {
    boolean ffEnabled = this.ldHelper.isFFEnabled(GENAI_SUMMARY_FF, FF_CONTEXT_KIND);
    Map<String, Object> responseMap = Collections.singletonMap("data", Boolean.toString(ffEnabled));
    return ResponseEntity.ok(responseMap);
  }

  @GetMapping("/duplicateIntentEnableFlag")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForDuplicateIntentEnabled() {
    boolean ffEnabled = this.ldHelper.isFFEnabled(ENABLE_DUPLICATE_INTENTS, FF_CONTEXT_KIND);
    Map<String, Object> responseMap = Collections.singletonMap("data", Boolean.toString(ffEnabled));
    return ResponseEntity.ok(responseMap);
  }

  @GetMapping("/sumEE")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForSummaryEditEnhancementsEnabled() {
    boolean ffEnabled = this.ldHelper.isFFEnabled(SUMMARY_EDIT_ENHANCEMENT_FLAG, FF_CONTEXT_KIND);
    Map<String, Object> responseMap = Collections.singletonMap("data", Boolean.toString(ffEnabled));
    return ResponseEntity.ok(responseMap);
  }

  @GetMapping("/agent-steps")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForAgentSteps() {
    return ResponseEntity.ok(Collections.singletonMap("data",
        Boolean.toString(this.ldHelper.isFFEnabled(ENABLE_AGENT_STEPS, FF_CONTEXT_KIND))));
  }

  @GetMapping("/gen-ai-enhancements")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForGenAiEnhancements() {
    return ResponseEntity.ok(Collections.singletonMap("data",
        Boolean.toString(this.ldHelper.isFFEnabled(XCONSOLE_GENAI_SUMMARY_ENHANCEMENTS, FF_CONTEXT_KIND))));
  }
  @GetMapping("/auto-submit-ff")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForAutoSubmit() {
    return ResponseEntity.ok(Collections.singletonMap("data",
        Boolean.toString(this.ldHelper.isFFEnabled(UASSIST_AUTO_SUBMIT_FF, FF_CONTEXT_KIND))));
  }

  @GetMapping("/gen-ai-entities-enhancements")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForGenAiEntitiesEnhancements() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-summary-update")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForGQLSummaryUpdateEnhancements() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_GQL_SUMMARY_UPDATE_FF, FF_CONTEXT_KIND))));
  }
  @GetMapping("/uassist-gen-ai-entities-to-llm")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForGenAiEntitiesToLLM() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-promises-under-genai-summary")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForPromiseInGenAISummary() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_PROMISE_IN_GEN_AI_SUMMARY, FF_CONTEXT_KIND))));
  }

  @GetMapping("/ai-model-under-experience-ff")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForAiModelUnderExperience() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_AI_MODEL_UNDER_EXPERIENCE_FF, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-genai-editing-enhancements-ff")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForGenAIEditingEnhancements() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_GENAI_EDITING_ENHANCEMENTS_FF, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-stream-summary-ff")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForStreamSummary() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_STREAM_SUMMARY_FF, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-genai-target-language-ff")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForGenAITargetLanguage() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(UASSIST_GENAI_TARGET_LANGUAGE_FF, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-genai-opt-in-out-ff")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForOptInOut() {
    return ResponseEntity.ok(Collections.singletonMap("data",
        Boolean.toString(this.ldHelper.isFFEnabled(UASSIST_GENAI_OPT_IN_OUT_FF, FF_CONTEXT_KIND))));
  }

  @GetMapping("/uassist-enable-ng-template-based-genai-summary")
  public ResponseEntity<Map<String, Object>> getFeatureFlagForTemplateBasedGenAISummary() {
    return ResponseEntity.ok(Collections.singletonMap("data", Boolean.toString(
        this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
            FF_CONTEXT_KIND))));
  }
}
