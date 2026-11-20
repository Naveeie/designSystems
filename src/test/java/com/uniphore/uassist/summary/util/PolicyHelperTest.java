package com.uniphore.uassist.summary.util;

import com.uniphore.conversations.v1.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.assertEquals;

class PolicyHelperTest {

  @InjectMocks
  private PolicyHelper policyHelper;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testIsConversationBlocked_WithBlockListPolicy() {
    ConversationEventEnvelope conversationEventEnvelope =
        ConversationEventEnvelope.newBuilder().setConversationId("test-conversation-id")
            .setTenantId("test-tenant-id").setStateUpdated(
                ConversationEventEnvelope.StateUpdated.newBuilder().setState(
                        State.newBuilder().setState(ConversationState.CONVERSATION_STATE_FINISHED).build())
                    .build()).setEnvironment("test-environment").addAllPolicies(List.of(
                Policy.newBuilder().setPolicyId("1234")
                    .setActionType(PolicyActionType.POLICY_ACTION_TYPE_BLOCKLIST).build())).build();

    // Act
    boolean result = policyHelper.isConversationBlocked(conversationEventEnvelope);
    // Assert
    Assertions.assertTrue(result);

  }

  @Test
  void testIsConversationBlocked_WithBlockWithPolicyAdded() {
    ConversationEventEnvelope conversationEventEnvelope =
        ConversationEventEnvelope.newBuilder().setConversationId("test-conversation-id")
            .setTenantId("test-tenant-id").setEnvironment("test-environment").setPoliciesAdded(
                ConversationEventEnvelope.PoliciesAdded.newBuilder().addAllPolicies(List.of(
                    Policy.newBuilder().setPolicyId("1234")
                        .setActionType(PolicyActionType.POLICY_ACTION_TYPE_BLOCKLIST).build())).build())
            .addAllPolicies(List.of(Policy.newBuilder().setPolicyId("1234")
                .setActionType(PolicyActionType.POLICY_ACTION_TYPE_ALLOWLIST).build())).build();

    // Act
    boolean result = policyHelper.isConversationBlocked(conversationEventEnvelope);
    // Assert
    Assertions.assertFalse(result);
  }
}
