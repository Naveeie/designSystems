package com.uniphore.uassist.summary.dao;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.uniphore.uassist.summary.dao.documents.AgentEditsDoc;

@Repository
public interface AgentEditsRepository extends MongoRepository<AgentEditsDoc, String> {
  
  void deleteAgentEditsByTenantId(final String tenantId);

}
