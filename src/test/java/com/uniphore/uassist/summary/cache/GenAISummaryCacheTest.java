package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.uassist.summary.ApplicationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenAISummaryCacheTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testDefaultValues() {
    GenAISummaryCache cache = new GenAISummaryCache();
    assertFalse(cache.isOnDemandSummaryEnabled());
    assertFalse(cache.isAcwSummaryEnabled());
    assertFalse(cache.isFormatEnabled());
    assertNull(cache.getSummaryFormat());
    assertFalse(cache.isShowUndSpkEntity());
    assertNull(cache.getSectionMap());
  }

  @Test
  void testBuilder() {
    GenAISummaryCache.SummaryFormat format = getSummaryFormat();
    GenAISummaryCache.SummaryOptIn optIn = getSummaryOptIn();
    GenAISummaryCache.SpokenEntity entity =
        GenAISummaryCache.SpokenEntity.builder().entityId("entity-id").variableId("variable-id")
            .build();
    GenAISummaryCache cache =
        GenAISummaryCache.builder().isOnDemandSummaryEnabled(true).acwSummaryEnabled(true)
            .formatEnabled(true).summaryFormat(format).summaryOptIn(optIn).showUndSpkEntity(true)
            .sectionMap(Collections.singletonMap("section1", Collections.singletonList(entity)))
            .build();
    assertTrue(cache.isOnDemandSummaryEnabled());
    assertTrue(cache.isAcwSummaryEnabled());
    assertTrue(cache.isFormatEnabled());
    assertNotNull(cache.getSummaryFormat());
    assertNotNull(cache.getSummaryOptIn());
    assertTrue(cache.isShowUndSpkEntity());
    assertNotNull(cache.getSectionMap());
    assertEquals(1, cache.getSectionMap().size());
    assertEquals(1, cache.getSectionMap().get("section1").size());
    assertEquals(entity, cache.getSectionMap().get("section1").get(0));
  }

  private static GenAISummaryCache.SummaryFormat getSummaryFormat() {
    GenAISummaryCache.SummaryFormat format =
        GenAISummaryCache.SummaryFormat.builder().callReason("Reason").agentSteps("Steps")
            .outcome("Outcome").build();
    return format;
  }

  private static GenAISummaryCache.SummaryOptIn getSummaryOptIn() {
    return GenAISummaryCache.SummaryOptIn.builder().isCallReasonEnabled(true)
        .isAgentStepsEnabled(true).isOutcomeEnabled(true).isCustomerSatisfactionEnabled(true)
        .build();
  }

  @Test
  void testSerialization() throws Exception {
    GenAISummaryCache cache =
        GenAISummaryCache.builder().isOnDemandSummaryEnabled(true).acwSummaryEnabled(false)
            .formatEnabled(true).summaryFormat(
                GenAISummaryCache.SummaryFormat.builder().callReason("Reason").agentSteps("Steps")
                    .outcome("Outcome").build()).summaryOptIn(
                GenAISummaryCache.SummaryOptIn.builder().isAgentStepsEnabled(true)
                    .isCallReasonEnabled(true).isOutcomeEnabled(true)
                    .isCustomerSatisfactionEnabled(true).build()).showUndSpkEntity(false)
            .sectionMap(new HashMap<>()).build();
    String json = objectMapper.writeValueAsString(cache);
    GenAISummaryCache deserializedCache = objectMapper.readValue(json, GenAISummaryCache.class);
    assertEquals(cache, deserializedCache);
  }
}
