package com.uniphore.uassist.summary.common;

import com.uniphore.uassist.summary.ApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommonsTest {

  @Test
  public void testGetErrorResponse() {
    // Given
    String error = "Some error occurred";
    HttpStatus status = HttpStatus.BAD_REQUEST;
    ResponseEntity<Map<String, Object>> response = Commons.getErrorResponse(error, status);
    assertEquals(status, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertTrue(body != null);
    assertEquals(error, body.get(Constants.ERRORS));
  }

  @Test
  public void testCombineListsToSetBothListsEmpty() {
    List<String> list1 = Collections.emptyList();
    List<String> list2 = Collections.emptyList();
    Set<String> result = Commons.combineListsToSet(list1, list2);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testCombineListsToSetFirstListEmpty() {
    List<String> list1 = Collections.emptyList();
    List<String> list2 = Arrays.asList("a", "b", "c");
    Set<String> result = Commons.combineListsToSet(list1, list2);
    Set<String> expected = new HashSet<>(list2);
    assertEquals(expected, result);
  }

  @Test
  public void testCombineListsToSetSecondListEmpty() {
    List<String> list1 = Arrays.asList("a", "b", "c");
    List<String> list2 = Collections.emptyList();
    Set<String> result = Commons.combineListsToSet(list1, list2);
    Set<String> expected = new HashSet<>(list1);
    assertEquals(expected, result);
  }

  @Test
  public void testCombineListsToSetBothListsNonEmpty() {
    List<String> list1 = Arrays.asList("a", "b", "c");
    List<String> list2 = Arrays.asList("b", "c", "d");
    Set<String> result = Commons.combineListsToSet(list1, list2);
    Set<String> expected = new HashSet<>(Arrays.asList("a", "b", "c", "d"));
    assertEquals(expected, result);
  }

  @Test
  public void testGetValidStringWithNull() {
    String input = null;
    String result = Commons.getValidString(input);
    assertEquals(StringUtils.EMPTY, result);
  }

  @Test
  public void testGetValidStringWithEmptyString() {
    String input = "";
    String result = Commons.getValidString(input);
    assertEquals(StringUtils.EMPTY, result);
  }

  @Test
  public void testGetValidStringWithNonEmptyString() {
    String input = "Valid String";
    String result = Commons.getValidString(input);
    assertEquals(input, result);
  }
}
