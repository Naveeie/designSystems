package com.uniphore.uassist.summary.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.uniphore.uassist.summary.dao.documents.GenAISummaryConfigDoc;

@Repository
public interface GenAISummaryConfigRepo extends MongoRepository<GenAISummaryConfigDoc, String> {

  Optional<GenAISummaryConfigDoc> findGenAISummaryConfigByExperienceIdAndTenantIdAndEnvironment(
      final String experienceId, final String tenantId, final String environment);

  List<GenAISummaryConfigDoc> findGenAISummaryConfigsByExperienceIdInAndTenantIdAndEnvironment(
      final List<String> experienceId, final String tenantId, final String environment);

  void deleteGenAISummaryConfigByTenantIdAndEnvironment(final String tenantId,
      final String environment);

  void deleteGenAISummaryConfigByTenantId(final String tenantId);
  
  void deleteGenAISummaryConfigByTenantIdAndEnvironmentAndExperienceId(final String tenantId,
      final String environment,final String experienceId);

  List<GenAISummaryConfigDoc> findGenAISummaryConfigByTenantIdAndEnvironment(final String tenantId,
      final String environment);
}
