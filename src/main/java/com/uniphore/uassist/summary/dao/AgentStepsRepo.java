package com.uniphore.uassist.summary.dao;

import com.uniphore.uassist.summary.dao.documents.AgentStepsDoc;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentStepsRepo extends MongoRepository<AgentStepsDoc, String> {
  @DeleteQuery(value = "{'tenantId' : $0}")
  void deleteByTenantId(String tenantId);
}
