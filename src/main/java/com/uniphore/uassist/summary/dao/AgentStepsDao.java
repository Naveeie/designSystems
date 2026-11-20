package com.uniphore.uassist.summary.dao;

import com.uniphore.uassist.summary.dao.documents.AgentStepsDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class AgentStepsDao {
  @Autowired
  private AgentStepsRepo agentStepsRepo;

  @Async("threadPoolMongoExecutor")
  public void saveAgentSteps(final AgentStepsDoc agentStepsDoc) {
    log.debug("Saving agentsteps to Mongo tenantId : {}, environment : {} ConverastionId : {}",
        agentStepsDoc.getTenantId(), agentStepsDoc.getEnvironment(),
        agentStepsDoc.getConversationId());
    this.agentStepsRepo.save(agentStepsDoc);
  }

  public Optional<AgentStepsDoc> getAgentStepsByConvId(final String conversationId) {
    log.debug("get agentsteps from Mongo ConverastionId : {}", conversationId);
    return this.agentStepsRepo.findById(conversationId);
  }
}
