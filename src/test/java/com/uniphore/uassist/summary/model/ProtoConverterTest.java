package com.uniphore.uassist.summary.model;

import com.uniphore.summary.v1.Header;
import com.uniphore.summary.v1.IntentSummary;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.dao.documents.IntentSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtoConverterTest {

  @Test
  void testIntentTemplateToSummaryProto() {
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("convId").tenantId("tenantId").environment("dev").experienceId("experienceId")
            .build();
    List<IntentSummaryDoc> intentSummaryDocs = new ArrayList<>();
    List<AgentStepPojo> agentSteps = new ArrayList<>();

    Summary summary =
        ProtoConverter.intentTemplateToSummaryProto(summaryDoc, intentSummaryDocs, agentSteps);

    assertNotNull(summary);
    assertFalse(summary.getAgentEdit());
  }

  @Test
  void testGetSummaryProto() {
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("convId").tenantId("tenantId").environment("dev").experienceId("experienceId")
            .build();
    Header header = Mockito.mock(Header.class);
    IntentSummary intentSummary = IntentSummary.newBuilder().build();
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    intentSummaryMap.put("intent", intentSummary);
    List<AgentStepPojo> agentSteps = new ArrayList<>();
    Boolean agentEdits = true;

    Summary summary =
        ProtoConverter.getSummaryProto(summaryDoc, header, intentSummaryMap, agentSteps,
            agentEdits);

    assertNotNull(summary);
    assertEquals(agentEdits, summary.getAgentEdit());
  }

  @Test
  void testGetSummaryProto_IfHeaderIsNull() {
    SummaryDoc summaryDoc =
        SummaryDoc.builder().conversationId("convId").tenantId("tenantId").environment("dev").experienceId("experienceId")
            .build();
    IntentSummary intentSummary = IntentSummary.newBuilder().build();
    Map<String, IntentSummary> intentSummaryMap = new HashMap<>();
    intentSummaryMap.put("intent", intentSummary);
    List<AgentStepPojo> agentSteps = new ArrayList<>();
    Boolean agentEdits = true;

    Summary summary =
        ProtoConverter.getSummaryProto(summaryDoc, null, intentSummaryMap, agentSteps, agentEdits);

    assertNotNull(summary);
    assertEquals(agentEdits, summary.getAgentEdit());
  }
}
