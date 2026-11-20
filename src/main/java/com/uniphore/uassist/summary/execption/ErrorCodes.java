package com.uniphore.uassist.summary.execption;

import java.util.HashMap;
import java.util.Map;

public class ErrorCodes {

  private ErrorCodes() {};

  private static Map<String, String> messages = new HashMap<>();

  public static final String SERVER_ERROR = "5000"; // used for coding contant
  public static final String JSON_ERROR = "5100";
  public static final String JSON_FILE_EMPTY = "5101";
  public static final String CONTENT_TYPE_INVALID = "5102";
  public static final String HTML_CONTENT_INVALID = "5103";

  public static final String FOLDER_CONTENT_EMPTY = "5104";
  public static final String ENTITY_ID_NOT_EMPTY = "50023";
  public static final String VARIABLE_ID_NOT_EMPTY = "50024";
  public static final String TENANT_ID_NOT_EMPTY = "50025";
  public static final String ENVIRONMENT_ID_NOT_EMPTY = "50026";
  public static final String BUSINESS_PROCESS_ID_NOT_EMPTY = "50027";
  public static final String INTENT_ID_NOT_EMPTY = "50028";
  public static final String MUST_NOT_BE_EMPTY ="50029";

  public static final String INSTANCE_ID_NOT_EMPTY = "50038";
  public static final String EXPERIENCE_ID_NOT_EMPTY = "50029";
  public static final String TEMPLATE_NOT_CREATED = "50030";
  public static final String CONVERSATION_ID_NOT_EMPTY = "50031";
  public static final String INTENT_SUMMARY_ALREADY_DETECTED_BY_AI = "50032";
  public static final String INTENT_SUMMARY_NOT_FOUND = "50033";
  public static final String CONVERSATION_NOT_FOUND = "50037";
  public static final String GENAISUMMARYCONFIG_NOT_FOUND = "50038";
  public static final String GENAISUMMARYCONFIG_NOT_CREATED = "50039";
  public static final String CONVERSATION_ID_EMPTY = "50040";
  public static final String SUMMARY_TEMPLATE_ID_EMPTY = "50041";
  public static final String INCORRECT_VERSION = "50042";
  public static final String INTENT_TEMPLATE_MAPPING_NOT_FOUND = "50043";
  public static final String INTENT_TEMPLATE_MAPPINGS_NOT_FOUND = "50044";

  static {

    messages.put(SERVER_ERROR, "Internal Server Error.");
    messages.put(JSON_ERROR, "Invalid json");
    messages.put(JSON_FILE_EMPTY, "Json may not be empty");
    messages.put(CONTENT_TYPE_INVALID, "Invalid content type");
    messages.put(HTML_CONTENT_INVALID, "Invalid html content");
    messages.put(ENTITY_ID_NOT_EMPTY, "Entity id may not be empty");
    messages.put(VARIABLE_ID_NOT_EMPTY, "Variable id may not be empty");
    messages.put(TENANT_ID_NOT_EMPTY, "Tenant id may not be empty");
    messages.put(ENVIRONMENT_ID_NOT_EMPTY, "Environment id may not be empty");
    messages.put(BUSINESS_PROCESS_ID_NOT_EMPTY, "Business process id may not be empty");
    messages.put(INTENT_ID_NOT_EMPTY, "Intent id may not be empty");
    messages.put(EXPERIENCE_ID_NOT_EMPTY, "Experience id may not be empty");
    messages.put(TEMPLATE_NOT_CREATED, "Template not created");
    messages.put(CONVERSATION_ID_NOT_EMPTY, "Conversation id may not be empty");
    messages.put(INTENT_SUMMARY_ALREADY_DETECTED_BY_AI, "Intent is already detected by AI");
    messages.put(INTENT_SUMMARY_NOT_FOUND, "Intent summary not found");
    messages.put(CONVERSATION_NOT_FOUND, "Conversation not found");
    messages.put(INSTANCE_ID_NOT_EMPTY, "instance id not found");
    messages.put(CONVERSATION_ID_EMPTY, "Conversation ID is empty");
    messages.put(SUMMARY_TEMPLATE_ID_EMPTY, "Summary Template ID is empty");
    messages.put(INCORRECT_VERSION, "Version should be a whole number and greater than zero");
    messages.put(INTENT_TEMPLATE_MAPPING_NOT_FOUND, "Intent to Summary Template mapping not found");
    messages.put(INTENT_TEMPLATE_MAPPINGS_NOT_FOUND, "Intent to Summary Template mappings not found for experience");
  }

  public static String getMessage(String code) {
    return messages.get(code);
  }

}
