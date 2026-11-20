package com.uniphore.uassist.summary.dao;

import java.util.List;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.model.SummaryEventAggrByEvent;

@Repository
public interface SummaryUpdateLogDao extends MongoRepository<SummaryUpdateEventLog, String> {
  @Query(value = "{'conversationId': ?0}", sort = "{'eventTime': 1}")
  List<SummaryUpdateEventLog> findEventLogByConversationId(final String conversationId);

  @Aggregation(pipeline = {"{$match: {'conversationId': ?0}}",
      "{$group: {_id: '$eventType', eventData: { $push: '$$ROOT'}}}",
      "{$project: {event: '$_id', eventData: 1, _id: 0}}"})
  List<SummaryEventAggrByEvent> getAggrEventDataForConversationId(final String conversationId);

  void deleteSummaryEventLogByTenantId(final String tenantId);
}
