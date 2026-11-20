package com.uniphore.uassist.summary.common;

public class Constants {
  public static final String DEPLOYMENT_API = "deployment_api";
  public static final String AGENT_ACTIONS = "agent-actions";
  public static final String AGENT_ACTION = "agent_action";
  public static final String CALL_REASON = "call-reasons";
  public static final String CALL_OUTCOME = "call-outcome";
  public static final String CUSTOMER_SENTIMENT = "customer-sentiment";
  public static final String TENANT_ID = "tenant-id";
  public static final String META_KEY_ENVIRONMENT = "environment";
  public static final String CONVERSATION_HYPHEN_ID = "conversation-id";
  public static final String GEN_AI = "gen_AI";
  public static final String IGNORE = "ignore";
  public static final String DETECTED_BY_AGENT = "agent";
  public static final String DETECTED_BY_AI = "ai";
  public static final String TAG_FLOW_ENDED_EVENT = "flow_end";
  public static final String CREATE = "create";
  public static final String ADD_AGENT_STEP = "add_agent_step";
  public static final String DELETE_AGENT_STEP = "delete_agent_step";
  public static final String UPDATE_AGENT_STEP = "update_agent_step";

  public static final String CONVERSATION_IDS = "conversation-ids";
  public static final String ACTION_TYPE = "action_type";
  public static final String SUMMARY = "summary";
  public static final String EVENT_TYPE = "event_type";
  public static final String DATA = "data";
  public static final String TAG_ENTITY_UPDATE = "entity_updated";
  public static final String SUMMARY_ENTITY_PROCESSED = "summary_entity_processed";
  public static String ExpId = "experienceId";
  public static final String GEN_AI_SUMMARY_LLM_INFERENCE_API_LATENCY_UASSIST_SUMMARY =
      "gen_ai_summary_llm_inference_api_latency_uassist_summary";

  private Constants() {}

  public static final String SAVE = "save";
  public static final String DELETE = "delete";
  public static final String UPDATE = "update";
  public static final String FAILURE = "failure";
  public static final String ERRORS = "errors";
  public static final String ERROR = "error";
  public static final String SUCCESS = "success";
  public static final String FAILED = "failed";
  public static final String AUTHORIZATION = "Authorization";
  public static final String EMPTY = "";
  public static final String EMPTY_SPACE = " ";
  public static final String X_USERNAME = "X-Username";
  public static final String REFERER = "Referer";
  public static final long DEFAULT_USERID = 1;

  /* platform telemetry constants */
  public static final String TAG_ENTITY_ADD = "entity_add";
  public static final String TAG_INTENT_ADD = "intent_add";
  public static final String TAG_INTENT_ADD_AGENT = "intent_add_agent";
  public static final String TAG_INTENT_DELETE = "intent_delete";
  public static final String TAG_VARIABLE_ADD = "variable_add";
  public static final String TAG_VARIABLE_UPDATE = "variable_update";
  public static final String TAG_CONVERSATION_START = "conversation_start";
  public static final String TAG_TYPE = "type";

  public static final String METHOD_TYPE = "method_type";
  public static final String LATENCY = "latency";
  public static final String UNDERSCORE_SEPERATOR = "%s_%s";
  public static final String TBD = "TBD";
  public static final String REGX_FOR_NAME_50_ANY_LANG =
      "^[^`!@#$%^&*~,.<>;':\"/\\[\\\\\\]\\|\\{\\}()=\\+]{0,50}$";

  // special characters not allowed
  public static final String REGEX_FOR_NAME_SPECIAL_CHARS_NOT_ALLOW =
      "^[^&\\*\\\"~<>/\\[\\\\\\]\\|\\{\\}()=\\+]{0,}$";

  public static final String REGX_EVENT_TYPE = "INTENT|ENTITY|VARIABLE";
  public static final String USER = "user";
  public static final String BEARER = "Bearer";
  public static final String KEYCLOAK = "keycloak";
  public static final String HEADER_ENTITY_MAP_KEY = "header-entity.map";
  public static final String HEADER_VARIABLE_MAP_KEY = "header-variable.map";
  public static final String HEADER_TEMPLATE_KEY = "header-template";

  public static final String AI = "AI";
  public static final String AGENT = "AGENT";

  public static final String INTENT = "INTENT";
  public static final String HEADER = "HEADER";
  public static final String DISPOSITION = "DISPOSITION";
  public static final String INTENT_TEMPLATE_KEY = "intent-template";
  public static final String DISPOSITION_VALUES_KEY = "disposition-values";
  public static final String GEN_AI_SUMMARY = "GEN_AI_SUMMARY";
  public static final String SUMMARY_DETAILS_FOLDER = "SUMMARY_DETAILS_FOLDER";
  public static final String SUMMARY_DETAILS_FILE = "SUMMARY_DETAILS_FILE";
  public static final String OPERATIONAL_REPORT = "OPERATIONAL_REPORT";
  public static final String SUCCESS_STATUS_CODE = "success";
  public static final String FAILURE_STATUS_CODE = "failure";

  public static final String SUMMARIES = "summaries";

  public static final String CREATE_GEN_AI_SUMMARY = "create_gen_ai_summary";

  public static final String GEN_AI_AUTO_SUBMIT = "auto_submit_gen_ai";

  public static final String EDIT_GEN_AI_SUMMARY = "edit_gen_ai_summary";

  public static final String LLM_GEN_AI_SUMMARY_REQUEST = "llm_gen_ai_summary_request";

  public static final String SUMMARY_PUBLISHED = "summary_published";
  public static final String SUMMARY_GEN_AI_PUBLISHED = "summary_gen_ai_published";
  public static final String SUMMARY_INTENT_PROCESSED = "summary_intent_processed";
  public static final String SUMMARY_SUBMITTED = "summary_submitted";
  public static final String SUMMARY_SUBMITTED_TYPE = "summary_submitted_type";
  public static final String SUMMARY_VARIABLE_PROCESSED = "summary__variable_processed";
  public static final String SUMMARY_CREATED = "summary_created";
  public static final String GENAI_SUMMARY_FORMAT_PATTERN = "(?<=\\.) (?=[A-Z])";
  public static final String GENAI_SUMMARY_BULLET_FORMAT = "(?<=[\\.?!])\\s*";
  public static final String NEW_LINE = "\n";
  public static final String RULE = "RULE";
  public static final String PARAGRAPH_OPEN = "<div>";
  public static final String PARAGRAPH_CLOSE = "</div>";
  public static final String BOLD_OPEN = "<strong>";
  public static final String BOLD_CLOSE = "</strong>";
  public static final String BOLD_CLOSE_WITH_COLON = ":</strong>";

  public static final String DIV_REPLACE = "<div>(.*?)</div>";
  public static final String REMOVE_TAG =
      "<(?!strong)(?!/strong)(?!div>)(?!/div>)(?!n>)(?!/n>)([^>]+)(?!\\s*<span)>";
  public static final String REPLACE_DIV_NEW_LINE_OPEN = "(?i)<div>";
  public static final String REPLACE_DIV_NEW_LINE_CLOSE = "(?i)</div>";
  public static final String DIV_NEW_LINE_OPEN = "<div><n>";
  public static final String DIV_NEW_LINE_CLOSE = "<n></div>";
  public static final String BOLD_WRAPPER_PATTERN = "<strong>$1</strong>";
  public static final String EOF_IN_ID = "EOF";
  public static final String GEN_AI_SECTION_PATTERN = "<div><n>%s<n></div>";
  public static final String GEN_AI_PATTERN_TEXT = "<n>%s<n>";
  public static final String GEN_AI_IN_LINE = "in-line";
  public static final String GEN_AI_END_OF_SECTION_FOR_DISPLAY = "end of section";
  public static final String GEN_AI_END_OF_SECTION_FOR_DISPLAY_AND_MENTION =
      "end of the section with mention label";
  public static final String SPAN_WITH_VARIABLE =
      "<span variable-id='%s' variable-instance-id='%s' value='%s' contenteditable='false'> %s </span>";

  public static final String UASSIST_GENAI_OPT_IN_OUT_FF = "uassist-genai-opt-in-out-ff";
  public static final String TARGET_PRODUCTS_ENABLE_FF =
      "platform-conversations-useTargetProductsField";
  public static final String GENAISUMMARY_METRIC = "gen_ai_metric_status_uassist_summary";
  public static final String SUBMIT_METRIC = "uassist_summary_submit_metric";
  public static final String GEN_AI_SUMMARY_EDITED_AGENT_UI = "gen_ai_summary_edited_agent_ui";
  public static final String GEN_AI_SUMMARY_STATUS_AGENT_UI = "gen_ai_summary_status_agent_ui";
  public static final String GENAI_SUMMARY_LATENCY_METRIC =
      "gen_ai_summary_latency_uassist_summary";
  public static final String RETURN_CODE_200 = "200";
  public static final String RETURN_CODE_204 = "204";
  public static final String RETURN_CODE_404 = "404";
  public static final String RETURN_CODE_400 = "400";
  public static final String RETURN_CODE_500 = "500";
  public static final String MANUAL_SUBMIT       ="manual_submit";
  public static final String AUTO_SUBMIT         ="auto_submit";
  public static final String FORCED_AUTO_SUBMIT  ="forced_auto_submit";
  public static final String SUBMIT_TYPE  ="submit_type";
  public static final String UNMODIFIED  ="unmodified";

  public static final String AGENT_GEN_AI = "agent";
  public static final String CUSTOMER = "customer";
  public static final String AGENT_TURNS = "agent_turns";
  public static final String CUSTOMER_TURNS = "customer_turns";
  public static final String LAST_SPOKEN_CHANNEL = "last_spoken_channel";

  public static final String ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY = "fs-builder-mode";

  public static final String v1SUMMARY = "/v1/summaries";
  public static final String UASSIST_STATE_V2_UPDATE_FF = "uassist-state-v2-update-ff";
}
