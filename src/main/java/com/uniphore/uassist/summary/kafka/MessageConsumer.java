package com.uniphore.uassist.summary.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.uniphore.conversations.v1.ConversationEventEnvelope;
import com.uniphore.conversations.v1.ProductType;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.event.helper.ConversationEventProcessingHelper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import com.uniphore.uassist.summary.service.SummaryRuntimeService;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.PolicyHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Slf4j
public class MessageConsumer {

  public static final String UASSIST_PROCESS_CONVERSATION_EVENTS =
      "uassist-process-conversation-events";

  @Autowired
  private SummaryRuntimeService summaryRuntimeService;

  @Autowired
  private ConversationEventProcessingHelper eventHelper;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  @Autowired
  private PolicyHelper policyHelper;

  @Bean
  public Consumer<Message<byte[]>> conversationEvents() {
    return message -> {
      byte[] data = message.getPayload();
      String key =
          message.getHeaders().getOrDefault(KafkaHeaders.RECEIVED_KEY, "unknown").toString();
      try {
        log.debug("conversation event message key: {}", key);
        String[] split = key.split("_");
        if (split.length > 0 && StringUtils.isNotBlank(split[0])) {
          ContextSetter.setContext(split[0], "dev");

          if (!ldHelper.isFFEnabled(UASSIST_PROCESS_CONVERSATION_EVENTS,
              LaunchDarklyConstants.FF_CONTEXT_KIND)) {
            log.debug("conversation events not enabled key: {}", key);
            return;
          }
        }

        ConversationEventEnvelope conversationEventEnvelope =
            ConversationEventEnvelope.newBuilder().build().getParserForType().parseFrom(data);

        ContextSetter.setContextWithSpan("uassist-summary", "conversation-events",
            conversationEventEnvelope.getConversationId(), conversationEventEnvelope.getTenantId(),
            conversationEventEnvelope.getEnvironment());

        boolean targetProductsffEnabled = ldHelper.isFFEnabled(Constants.TARGET_PRODUCTS_ENABLE_FF,
            LaunchDarklyConstants.FF_CONTEXT_KIND);

        if (targetProductsffEnabled && !conversationEventEnvelope.getTargetProductsList()
            .contains(ProductType.PRODUCT_TYPE_U_ASSIST.getNumber())) {
          return;
        }

        boolean ffEnabled =
            ldHelper.isFFEnabled(LaunchDarklyConstants.SUMMARY_PARALLEL_PROCESS_ENABLED,
                LaunchDarklyConstants.FF_CONTEXT_KIND);

        log.debug(
            "got conversation event for conversation: {}, parallel processing enabled: {}, event: {}",
            conversationEventEnvelope.getConversationId(), ffEnabled, conversationEventEnvelope);

        if (ffEnabled) {
          eventHelper.pushEventToConversationQueue(conversationEventEnvelope);
        } else {
          final boolean isConversationBlocked =
              policyHelper.isConversationBlocked(conversationEventEnvelope);
          if (isConversationBlocked) {
            log.info("conversation blocked for conversationId: {}",
                conversationEventEnvelope.getConversationId());
            return;
          }

          summaryRuntimeService.handleConversationEvent(conversationEventEnvelope,
              DateUtil.TIME.millis());
        }

      } catch (InvalidProtocolBufferException e) {
        ContextSetter.setContextWithSpanError(e);
        log.error("ProtocolBuffer TypeCasting for key: {} {}", key, "conversation-events", e);
      } catch (Exception e) {
        ContextSetter.setContextWithSpanError(e);
        log.error("Exception handling conversation event: ", e);
      } finally {
        ContextSetter.clearContext();
      }
    };
  }

  @Bean
  @RegisterReflectionForBinding(classes = AgentUpdateRequest.class)
  public Consumer<AgentUpdateRequest> agentUpdate() {
    return request -> {
      ContextSetter.setContextWithSpan("uassist-summary", "agent-update-in-kafka",
          request.getConversationId(), request.getTenantId(), request.getEnvironment());

      boolean ffEnabled =
          ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
              LaunchDarklyConstants.FF_CONTEXT_KIND);

      log.info("Got agent update request {}", request);

      if (ffEnabled) {
        eventHelper.pushEventToConversationQueueFromConsumer(request);
      } else {
        log.info("Ignoring agent update event from Kafka, FF not enabled");
      }
    };
  }
}
