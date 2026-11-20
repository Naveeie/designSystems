package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;

import com.uniphore.ri.common.tenant.TenantUtils;
import com.uniphore.uassist.summary.dao.documents.SummaryUpdateEventLog;
import com.uniphore.uassist.summary.model.SummaryEventAggrByEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SummaryRuntimeDao {

  @Autowired
  private SummaryRepository summaryRepository;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private SummaryUpdateLogDao summaryUpdateLogDao;

  public void saveSummaryDoc(SummaryDoc summaryDoc) {
    log.debug("Saving SummaryDoc to Mongo tenantId : {}, environment : {} ConverastionId : {}",
        summaryDoc.getTenantId(), summaryDoc.getEnvironment(), summaryDoc.getConversationId());
    //summaryRepository.save(summaryDoc);
    Query q = new Query(
        Criteria.where("conversationId").is(summaryDoc.getConversationId()).and("tenantId")
            .is(summaryDoc.getTenantId()));
    mongoTemplate.findAndReplace(q, summaryDoc, FindAndReplaceOptions.options().upsert());
  }

  public void deleteSummaryDoc(String conversationId) {
    log.debug("Deleting SummaryDoc from Mongo ConverastionId : {}", conversationId);
    Query query = new Query(Criteria.where("conversationId").is(conversationId).and("tenantId")
        .is(TenantUtils.getTenantDetails().getTenantId()).and("environment")
        .is(TenantUtils.getTenantDetails().getEnvironment()));
    mongoTemplate.remove(query, SummaryDoc.class);
  }

  @Async("threadPoolMongoExecutor")
  public void saveSummaryEventDoc(SummaryUpdateEventLog summaryUpdateEventLog) {
    log.debug("Saving SummaryEventDoc to Mongo tenantId : {}, environment : {} ConverastionId : {}",
        summaryUpdateEventLog.getTenantId(), summaryUpdateEventLog.getEnvironment(),
        summaryUpdateEventLog.getConversationId());
    summaryUpdateLogDao.save(summaryUpdateEventLog);
  }

  public Optional<SummaryDoc> getSummaryDoc(String conversationId) {
    log.debug("get SummaryDoc from Mongo ConversationId : {}", conversationId);
    return summaryRepository.findById(conversationId);
  }

  public List<SummaryDoc> getSummaryDocs(List<String> conversationIds) {
    log.debug("get SummaryDocs from Mongo ConversationIds : {}", conversationIds);
    return summaryRepository.findAllById(conversationIds);
  }

  public List<SummaryUpdateEventLog> getSummaryEventLog(String conversationId) {
    log.debug("get SummaryEventDoc from Mongo ConverastionId : {}", conversationId);
    return this.summaryUpdateLogDao.findEventLogByConversationId(conversationId);
  }

  public List<SummaryEventAggrByEvent> getSummaryAggrByEventForConvId(String conversationId) {
    log.debug("get summaryAggrByEventForConvId from Mongo ConverastionId : {}", conversationId);
    return this.summaryUpdateLogDao.getAggrEventDataForConversationId(conversationId);
  }

  public List<SummaryDoc> getSummaryDocsByExtConversationIds(
      final List<String> extConversationIds) {
    log.debug("get SummaryDoc from Mongo for extConversationIds: {}", extConversationIds);
    return this.summaryRepository.findByExtConversationIdIn(extConversationIds,
        TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment());
  }

  public SummaryDoc getSummaryByExtId(final String extConversationId) {
    log.debug("get SummaryDoc from Mongo for extConversationId: {}", extConversationId);
    return this.summaryRepository.findByExtConversationIdAndTenantIdAndEnvironment(
        extConversationId, TenantUtils.getTenantDetails().getTenantId(),
        TenantUtils.getTenantDetails().getEnvironment());
  }

}
