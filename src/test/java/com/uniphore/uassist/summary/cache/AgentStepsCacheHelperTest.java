package com.uniphore.uassist.summary.cache;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.model.AgentStepPojo;
import com.uniphore.uassist.summary.util.DateUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentStepsCacheHelperTest {

  @Mock
  private CacheOperations<Map<String, AgentStepPojo>> agentStepsCache;

  @InjectMocks
  private AgentStepsCacheHelper agentStepsCacheHelper;

  @Value("${app.redis.summary-cache.ttl:3600000}")
  private long cachettl;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testAddAgentSteps() {
    String convId = "conv1";
    AgentStepPojo step1 = new AgentStepPojo("id1", "text1", 1);
    AgentStepPojo step2 = new AgentStepPojo("id2", "text2", 2);
    List<AgentStepPojo> steps = Arrays.asList(step1, step2);
    Map<String, AgentStepPojo> cache = new HashMap<>();
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    List<AgentStepPojo> result = agentStepsCacheHelper.add(convId, steps);
    assertEquals(2, result.size());
    verify(agentStepsCache).put(anyString(), anyString(), any(Map.class), eq(cachettl));
  }

  @Test
  void testAddSingleAgentStep() {
    String convId = "conv1";
    AgentStepPojo step = new AgentStepPojo("id1", "text1", 1);
    Map<String, AgentStepPojo> cache = new HashMap<>();
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    List<AgentStepPojo> result = agentStepsCacheHelper.add(convId, step);
    assertEquals(1, result.size());
    verify(agentStepsCache).put(anyString(), anyString(), any(Map.class), eq(cachettl));
  }

  @Test
  void testGetAgentSteps() {
    String convId = "conv1";
    AgentStepPojo step = new AgentStepPojo("id1", "text1", 1);
    Map<String, AgentStepPojo> cache = Collections.singletonMap("id1", step);
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    Map<String, AgentStepPojo> result = agentStepsCacheHelper.get(convId);
    assertEquals(1, result.size());
    assertTrue(result.containsKey("id1"));
  }

  @Test
  void testGetAgentStepsAsList() {
    String convId = "conv1";
    AgentStepPojo step = new AgentStepPojo("id1", "text1", 1);
    Map<String, AgentStepPojo> cache = Collections.singletonMap("id1", step);
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    List<AgentStepPojo> result = agentStepsCacheHelper.getAsList(convId);
    assertEquals(1, result.size());
    assertTrue(result.contains(step));
  }

  @Test
  void testRemoveAgentSteps() {
    String convId = "conv1";
    agentStepsCacheHelper.remove(convId);
    verify(agentStepsCache).delete(anyString(), anyString());
  }

  @Test
  void testDeleteAgentStep() {
    String convId = "conv1";
    String stepId = "id1";
    Map<String, AgentStepPojo> cache = new HashMap<>();
    cache.put(stepId, new AgentStepPojo(stepId, "text", 1));
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    boolean result = agentStepsCacheHelper.delete(convId, stepId);
    assertTrue(result);
    verify(agentStepsCache).put(anyString(), anyString(), any(Map.class), eq(cachettl));
  }

  @Test
  void testUpdateAgentStep() {
    String convId = "conv1";
    String stepId = "id1";
    String newText = "updatedText";
    AgentStepPojo existingStep = new AgentStepPojo(stepId, "oldText", 1);
    Map<String, AgentStepPojo> cache = Collections.singletonMap(stepId, existingStep);
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    boolean result = agentStepsCacheHelper.update(convId, stepId, newText);
    assertTrue(result);
    assertEquals(newText, cache.get(stepId).getText());
    verify(agentStepsCache).put(anyString(), anyString(), any(Map.class), eq(cachettl));
  }

  @Test
  void testGetNextIndex() {
    String convId = "conv1";
    AgentStepPojo step1 = new AgentStepPojo("id1", "text1", 1);
    AgentStepPojo step2 = new AgentStepPojo("id2", "text2", 2);
    Map<String, AgentStepPojo> cache = new HashMap<>();
    cache.put(step1.getId(), step1);
    cache.put(step2.getId(), step2);
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    int nextIndex = agentStepsCacheHelper.getNextIndex(convId);
    assertEquals(3, nextIndex);
  }

  @Test
  void testAgentStepExist() {
    String convId = "conv1";
    String stepId = "id1";
    Map<String, AgentStepPojo> cache =
        Collections.singletonMap(stepId, new AgentStepPojo(stepId, "text", 1));
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    boolean exists = agentStepsCacheHelper.agentStepExist(convId, stepId);
    assertTrue(exists);
  }

  @Test
  void testAddAgentStepsWithEmptyList() {
    String convId = "conv1";
    List<AgentStepPojo> steps = Collections.emptyList();
    List<AgentStepPojo> result = agentStepsCacheHelper.add(convId, steps);
    assertTrue(result.isEmpty());
    verify(agentStepsCache, never()).put(anyString(), anyString(), any(Map.class), anyLong());
  }

  @Test
  void testAddSingleAgentStepWithNullCache() {
    String convId = "conv1";
    AgentStepPojo step = new AgentStepPojo("id1", "text1", 1);
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    List<AgentStepPojo> result = agentStepsCacheHelper.add(convId, step);
    assertEquals(1, result.size());
    verify(agentStepsCache).put(anyString(), anyString(), any(Map.class), eq(cachettl));
  }

  @Test
  void testGetAgentStepsWithNullCache() {
    String convId = "conv1";
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    Map<String, AgentStepPojo> result = agentStepsCacheHelper.get(convId);
    assertNull(result);
  }

  @Test
  void testGetAgentStepsAsListWithNullCache() {
    String convId = "conv1";
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    List<AgentStepPojo> result = agentStepsCacheHelper.getAsList(convId);
    assertTrue(result.isEmpty());
  }

  @Test
  void testRemoveAgentStepsWithLoggingEnabled() {
    String convId = "conv1";
    agentStepsCacheHelper.remove(convId);
    verify(agentStepsCache).delete(anyString(), anyString());
    // Additional logging verification can be done if needed
  }

  @Test
  void testDeleteAgentStepWithNullCache() {
    String convId = "conv1";
    String stepId = "id1";
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    boolean result = agentStepsCacheHelper.delete(convId, stepId);
    assertFalse(result);
    verify(agentStepsCache, never()).put(anyString(), anyString(), any(Map.class), anyLong());
  }

  @Test
  void testDeleteAgentStepWithIdNotFound() {
    String convId = "conv1";
    String stepId = "id1";
    Map<String, AgentStepPojo> cache = new HashMap<>();
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    boolean result = agentStepsCacheHelper.delete(convId, stepId);
    assertFalse(result);
    verify(agentStepsCache, never()).put(anyString(), anyString(), any(Map.class), anyLong());
  }

  @Test
  void testUpdateAgentStepWithNullCache() {
    String convId = "conv1";
    String stepId = "id1";
    String newText = "updatedText";
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    boolean result = agentStepsCacheHelper.update(convId, stepId, newText);
    assertFalse(result);
    verify(agentStepsCache, never()).put(anyString(), anyString(), any(Map.class), anyLong());
  }

  @Test
  void testUpdateAgentStepWithIdNotFound() {
    String convId = "conv1";
    String stepId = "id1";
    String newText = "updatedText";
    Map<String, AgentStepPojo> cache = new HashMap<>();
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    boolean result = agentStepsCacheHelper.update(convId, stepId, newText);
    assertFalse(result);
    verify(agentStepsCache, never()).put(anyString(), anyString(), any(Map.class), anyLong());
  }

  @Test
  void testGetNextIndexWithNullCache() {
    String convId = "conv1";
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    int nextIndex = agentStepsCacheHelper.getNextIndex(convId);
    assertEquals(1, nextIndex);
  }

  @Test
  void testGetNextIndexWithEmptyCache() {
    String convId = "conv1";
    Map<String, AgentStepPojo> cache = new HashMap<>();
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(cache);
    int nextIndex = agentStepsCacheHelper.getNextIndex(convId);
    assertEquals(1, nextIndex);
  }

  @Test
  void testAgentStepExistWithNullCache() {
    String convId = "conv1";
    String stepId = "id1";
    when(agentStepsCache.get(anyString(), anyString(), any(TypeReference.class))).thenReturn(null);
    boolean exists = agentStepsCacheHelper.agentStepExist(convId, stepId);
    assertFalse(exists);
  }

  @Test
  void testAddWithDebugLogging() {
    String convId = "testConvId";
    AgentStepPojo agentStep = AgentStepPojo.builder().text("testText").id("TestId").build();
    long startTime = DateUtil.TIME.millis();

    Logger log = (Logger) LoggerFactory.getLogger(AgentStepsCacheHelper.class);
    log.setLevel(Level.DEBUG);

    when(agentStepsCache.get(anyString(), eq(convId), any(TypeReference.class))).thenReturn(null);

    List<AgentStepPojo> result = agentStepsCacheHelper.add(convId, agentStep);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(agentStep.getId(), result.get(0).getId());
  }

  @Test
  void testAddWithTraceLogging() {
    String convId = "testConvId";
    AgentStepPojo agentStep = AgentStepPojo.builder().text("testText").id("TestId").build();
    long startTime = DateUtil.TIME.millis();

    Logger log = (Logger) LoggerFactory.getLogger(AgentStepsCacheHelper.class);
    log.setLevel(Level.TRACE);

    when(agentStepsCache.get(anyString(), eq(convId), any(TypeReference.class))).thenReturn(null);

    List<AgentStepPojo> result = agentStepsCacheHelper.add(convId, agentStep);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(agentStep.getId(), result.get(0).getId());
  }
}
