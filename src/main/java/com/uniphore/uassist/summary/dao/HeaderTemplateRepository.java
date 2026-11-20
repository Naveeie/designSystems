package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.uniphore.uassist.summary.dao.documents.HeaderTemplateDoc;

public interface HeaderTemplateRepository extends MongoRepository<HeaderTemplateDoc, String> {

  List<HeaderTemplateDoc> findHeaderTemplateByBusinessProcessIdInAndTenantIdAndEnvironment(
      final List<String> businessProcessIds, final String tenantId, final String environment);

  Optional<HeaderTemplateDoc> findHeaderTemplateByBusinessProcessIdAndTenantIdAndEnvironment(
      final String businessProcessId, final String tenantId, final String environment);

  void deleteHeaderTemplateByTenantIdAndEnvironment(final String tenantId,
      final String environment);

  void deleteHeaderTemplateByTenantId(final String tenantId);

  List<HeaderTemplateDoc> findHeaderTemplateByTenantIdAndEnvironment(final String tenantId,
      final String environment);

  @Query(value = "{ 'tenantId' : ?0, 'environment' : ?1 }", fields = "{ 'template' : 1}")
  List<String> findAllTemplateFields(String tenantId, String environment);
}
