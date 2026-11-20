package com.uniphore.uassist.summary.service;

import com.uniphore.uassist.summary.model.ExportImportSummary;
import com.uniphore.uassist.summary.model.ImportSummaryRequest;

public interface ExportImportService {
  void exportSummaryInThread(String experienceId, String requestId) throws Exception;

  void importSummaryInThread(ImportSummaryRequest importRequest, String token) throws Exception;

  void rollbackSummaryInThread(ExportImportSummary req, String experienceId, String requestId);
}
