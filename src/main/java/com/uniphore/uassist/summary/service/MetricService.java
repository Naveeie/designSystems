package com.uniphore.uassist.summary.service;

import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.helper.MetricHelper;
import com.uniphore.uassist.summary.util.ReportResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import lombok.extern.slf4j.Slf4j;

import com.uniphore.uassist.summary.util.DateUtil;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MetricService {

  @Value("${summary.csv.upload-path}")
  private String storageLocation;

  @Autowired
  private ExecutorService fixedThreadPoolExecutor;

  @Autowired
  private MetricHelper metricHelper;

  @Autowired
  private CSVService csvService;

  public ResponseEntity<ByteArrayResource> getSummaryDetailedReport(final List<String> conversationIds) throws IOException {

    log.info("Summary Events report conversationIds :{}", conversationIds);
    Set<String> conversationIdsSet = new HashSet<>(conversationIds);
    List<String[]> errorRows = new ArrayList<>();
    String path = this.storageLocation + File.separator + Constants.SUMMARY_DETAILS_FOLDER;
    log.info("Path of folder:{}",path);
    StringBuilder pathName = new StringBuilder(path);
    final File folder = new File(String.valueOf(pathName));
    CompletionService<ReportResponse> excutorCompletionService =
        new ExecutorCompletionService<>(fixedThreadPoolExecutor);
    for(String convId: conversationIdsSet) {
      excutorCompletionService.submit(() -> this.metricHelper.buildSummaryReport(convId, path));
    }
    for (int i = 0; i < conversationIds.size(); i++) {
      try {
        /**
         * We are checking completionService.take(), so we can get to know all the jobs are done or
         * not.
         */
        Future<ReportResponse> response = excutorCompletionService.take();
        final ReportResponse reportResponse = response.get();
        String[] responseValue = {reportResponse.getConversationId(), reportResponse.getStatus(), reportResponse.getErrorMessage()};
        log.info("Report response conversationID {} status {} ErrorMessage {} ",responseValue[0],responseValue[1], responseValue[2]);
        errorRows.add(responseValue);

      } catch (InterruptedException e) {
        log.error("Error in creating a Summary Events Report :- {}", e.getMessage());
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }

    try {
      this.csvService.dataToCSVFile(
          String.valueOf(pathName.append("Error.csv"))
              , errorRows);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
      return this.metricHelper.buildZipResponse(folder, "SummaryEvents_" + DateUtil.getTimestamp());

  }
}
