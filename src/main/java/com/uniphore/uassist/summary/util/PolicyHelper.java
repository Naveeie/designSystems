package com.uniphore.uassist.summary.util;

import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.PolicyActionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PolicyHelper {

  public boolean isConversationBlocked(ConversationEventEnvelope conversationEventEnvelope) {
    if (StringUtils.isNotEmpty(conversationEventEnvelope.getPoliciesAdded().toString())) {
      if (CollectionUtils.isNotEmpty(conversationEventEnvelope.getPoliciesList())) {
        log.info("Checking policies for conversationId:-> {}",
            conversationEventEnvelope.getConversationId());
        boolean noneMatch = false;
        if (!conversationEventEnvelope.getPoliciesList().isEmpty()) {
          noneMatch = conversationEventEnvelope.getPoliciesAdded().getPoliciesList().stream()
              .noneMatch(policy -> policy.getActionType()
                  .equals(PolicyActionType.POLICY_ACTION_TYPE_BLOCKLIST));
          log.info("Policies added is not empty for conversationId:-> {}, with noneMatch: {}",
              conversationEventEnvelope.getConversationId(), noneMatch);
          if (!noneMatch) {
            log.warn(
                "LIVE CALL FLOW (Summary): Call is blocked due to POLICY_ACTION_TYPE_BLOCKLIST for conversationId: {}",
                conversationEventEnvelope.getConversationId());
          }
        }
        return noneMatch;
      }
    }
    log.info("Not a PolicyAdded event for conversationId:-> {}",
        conversationEventEnvelope.getConversationId());
    return conversationEventEnvelope.getPoliciesList().stream().anyMatch(
        policy -> policy.getActionType().equals(PolicyActionType.POLICY_ACTION_TYPE_BLOCKLIST));
  }

}
