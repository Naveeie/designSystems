package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.uniphore.uassist.summary.dao.documents.IntentTemplateDoc;

@Repository
public interface IntentTemplateRepo extends MongoRepository<IntentTemplateDoc, String> {

  Optional<IntentTemplateDoc> findIntentTemplateByIntentIdAndTenantIdAndEnvironment(
      final String intentId, final String tenantId, final String environment);

  List<IntentTemplateDoc> findIntentTemplateByIntentIdInAndTenantIdAndEnvironment(
      final List<String> intentIds, final String tenantId, final String environment);

  void deleteIntentTemplateByTenantIdAndEnvironment(final String tenantId,
      final String environment);

  void deleteIntentTemplateByTenantId(final String tenantId);

  List<IntentTemplateDoc> findIntentTemplateByTenantIdAndEnvironment(final String tenantId,
      final String environment);

  @Query(value = "{ 'tenantId' : ?0, 'environment' : ?1 }", fields = "{ 'template' : 1}")
  List<String> findAllTemplateFields(String tenantId, String environment);

  List<IntentTemplateDoc> findIntentTemplateByExperienceIdAndTenantIdAndEnvironmentAndDeletedFalse(
      final String expId, final String tenantId, final String environment);
  
  void deleteIntentTemplateByExperienceIdAndTenantIdAndEnvironment(final String expId,
      final String tenantId, final String environment);
}
