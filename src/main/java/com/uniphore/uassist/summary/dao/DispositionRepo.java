package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.uniphore.uassist.summary.dao.documents.DispositionDoc;

@Repository
public interface DispositionRepo extends MongoRepository<DispositionDoc, String> {

  Optional<DispositionDoc> findDispositionByBusinessProcessIdAndTenantIdAndEnvironment(
      final String businessProcessId, final String tenantId, final String environment);

  List<DispositionDoc> findDispositionByBusinessProcessIdInAndTenantIdAndEnvironment(
      final List<String> businessProcessId, final String tenantId, final String environment);

  void deleteDispositionByTenantIdAndEnvironment(final String tenantId, final String environment);
  
  void deleteDispositionByTenantId(final String tenantId);

  List<DispositionDoc> findDispositionByTenantIdAndEnvironment(final String tenantId,
      final String environment);
}
