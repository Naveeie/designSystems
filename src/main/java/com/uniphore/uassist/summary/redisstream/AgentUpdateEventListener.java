package com.uniphore.uassist.summary.redisstream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniphore.featureflag.LaunchDarklyConstants;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.telemetry.context.ContextSetter;
import com.uniphore.uassist.summary.event.helper.ConversationEventProcessingHelper;
import com.uniphore.uassist.summary.event.pojo.AgentUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Slf4j
@Service
public class AgentUpdateEventListener
    implements StreamListener<byte[], MapRecord<byte[], byte[], byte[]>> {

  @Value("${redis.stream.agent-event-key:uassist-summary-agent-update}")
  private String agentUpdateStreamKey;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConversationEventProcessingHelper eventHelper;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  @Override
  public void onMessage(MapRecord<byte[], byte[], byte[]> record) {
    String message = record.getValue().entrySet().stream()
        .filter((e) -> AgentUpdateEventPublisher.MESSAGE.equalsIgnoreCase(new String(e.getKey())))
        .findFirst().map(v -> new String(v.getValue())).orElse(StringUtils.EMPTY);
    log.debug("OnMessage AgentUpdate:: {}", message);
    try {
      if (StringUtils.isNotEmpty(message)) {
        AgentUpdateRequest agentUpdateRequest =
            objectMapper.readValue(message, AgentUpdateRequest.class);

        if (ObjectUtils.isNotEmpty(agentUpdateRequest)) {
          ContextSetter.setContextWithSpan("uassist-summary", "agent-update-in-redis-stream",
              agentUpdateRequest.getConversationId(), agentUpdateRequest.getTenantId(),
              agentUpdateRequest.getEnvironment());
          log.debug("AgentUpdateEventListener::AgentupdateRequest host: {}  - consumed message:{} ",
              InetAddress.getLocalHost().getHostName(), agentUpdateRequest);
          boolean ffEnabled =
              this.ldHelper.isFFEnabled(LaunchDarklyConstants.UASSIST_AGENT_EDITS_QUEUE_PROCESSING,
                  LaunchDarklyConstants.FF_CONTEXT_KIND);
          if (ffEnabled) {
            this.eventHelper.pushEventToConversationQueueFromConsumer(agentUpdateRequest);
          } else {
            log.info(
                "AgentUpdateEventListener::ignoring agent update evet from kafka ff not enabled");
          }
        } else {
          log.debug("AgentUpdateEventListener::agent update reqeust is empty");
        }
      }
    } catch (Exception e) {
      log.error("AgentUpdateEventListener::Failed to the message from redis stream", e);
    }

  }
}
