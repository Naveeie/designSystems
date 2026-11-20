package com.uniphore.uassist.summary.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;;

public class Commons {

  private Commons() {}

  public static enum AgentAction {
    INTENT_ADD, INTENT_DELETE, INTENT_EDIT, HEADER_EDIT, GEN_AI_SUMMARY_EDIT, INVALID, AGENT_NOTES, DELETE_AGENT_STEP, ADD_AGENT_STEP, UPDATE_AGENT_STEP;
  }


  public static List<String> agentEditRequstType = new ArrayList<String>();

  static {
    agentEditRequstType.add(Constants.INTENT);
    agentEditRequstType.add(Constants.HEADER);
    agentEditRequstType.add(Constants.DISPOSITION);
  }

  public static ResponseEntity<Map<String, Object>> getErrorResponse(final Object error,
      HttpStatus status) {
    final Map<String, Object> output = new HashMap<>();
    output.put(Constants.ERRORS, error);
    return new ResponseEntity<>(output, status);
  }

  public static Set<String> combineListsToSet(List<String> list1, List<String> list2) {

    if (CollectionUtils.isEmpty(list1) && CollectionUtils.isEmpty(list2)) {
      return new HashSet<>();
    } else if (CollectionUtils.isEmpty(list1)) {
      return new HashSet<>(list2);
    } else if (CollectionUtils.isEmpty(list2)) {
      return new HashSet<>(list1);
    }

    Set<String> combinedSet = new HashSet<>(list1);
    combinedSet.addAll(list2);
    return combinedSet;
  }

  public static String getValidString(String s) {
    if (StringUtils.isEmpty(s)) {
      return StringUtils.EMPTY;
    }
    return s;
  }
}
