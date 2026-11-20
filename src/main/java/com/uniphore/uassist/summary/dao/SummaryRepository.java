package com.uniphore.uassist.summary.dao;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.uniphore.uassist.summary.dao.documents.SummaryDoc;

import java.util.List;

@Repository
public interface SummaryRepository extends MongoRepository<SummaryDoc, String> {

  void deleteSummaryByTenantId(final String tenantId);

  @Query("{'$and':[{'extConversationId': {'$in': ?0}},{'tenantId': ?1},{'environment': ?2}]}")
  List<SummaryDoc> findByExtConversationIdIn(final List<String> extConversationIds,
      final String tenantId, final String environment);

  SummaryDoc findByExtConversationIdAndTenantIdAndEnvironment(final String extConversationId,
      final String tenantId, final String environment);
}
