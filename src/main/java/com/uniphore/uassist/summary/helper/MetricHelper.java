package com.uniphore.uassist.summary.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.*;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.service.CSVService;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.ReportResponse;
import com.uniphore.uassist.summary.util.SummaryReportMetric;
import com.uniphore.uassist.summary.execption.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MetricHelper {

  @Autowired
  private SummaryRuntimeDao summaryRuntimeDao;

  @Value("${summary.csv.upload-path}")
  private String storageLocation;
  @Autowired
  private CSVService csvService;

  public ReportResponse buildSummaryReport(final String conversationId, final String path) {
    List<String[]> reportRows = new ArrayList<>();
    StringBuilder pathName = new StringBuilder(path);
    log.debug("Summary Events Report buildSummaryReport conversationId: {} and threadName :{}",
        conversationId, Thread.currentThread().getName());
    try {

      Optional<SummaryDoc> summaryDoc = this.summaryRuntimeDao.getSummaryDoc(conversationId);
      log.info("SummaryDoc - conversationId: {}", summaryDoc.get().getConversationId());
      if (!summaryDoc.isPresent()) {
        log.error("Error in fetching conversationId {} from DB", conversationId);
        return ReportResponse.builder().conversationId(conversationId).status("Failure")
            .errorMessage("ConversationId is not present in DB").build();
      }
      HeaderSummaryDoc headerSummary = summaryDoc.get().getHeaderSummary();
      if (headerSummary.getTemplate() != null) {
        log.info("Header summary:{}", headerSummary.getPlainText());
        SummaryReportMetric headerRecord = buildHeaderSummaryReport(headerSummary, conversationId);
        String[] value = {headerRecord.getConversationId(), headerRecord.getHeaderOrIntent(),
            headerRecord.getTimestamp(), headerRecord.getAgentSummary(),
            headerRecord.getCountOfEntities(), headerRecord.getCountOfVariables(),
            headerRecord.getDetectedBy(), headerRecord.getExpId()};
        log.info("Header values {}",value[0]);
        reportRows.add(value);
      }

      summaryDoc.get().getIntentSummaries().values().forEach(intent -> {
        SummaryReportMetric intentRecord = buildReportWithIntent(intent, conversationId);
        String[] value = {intentRecord.getConversationId(), intentRecord.getHeaderOrIntent(),
            intentRecord.getIntentId(), intentRecord.getIntentName(), intentRecord.getTimestamp(),
            intentRecord.getAgentSummary(), intentRecord.getCountOfEntities(),
            intentRecord.getCountOfVariables(), intentRecord.getDisposition(),
            intentRecord.getDetectedBy(), intentRecord.getSource(), intentRecord.getExpId()};
        log.info("Intent values {}",value[0]);
        reportRows.add(value);

      });

    } catch (Exception e) {
      log.error("Error in fetching conversationId {} :- {}", conversationId, e.getMessage());
      ReportResponse.builder().conversationId(conversationId).status("Failure")
          .errorMessage(e.getMessage());
    }

    try {
      String filePath = String.valueOf(
          pathName.append(File.separator).append(Constants.SUMMARY_DETAILS_FILE)
              .append(File.separator).append(conversationId).append("_")
              .append(DateUtil.getTimestamp()).append(".csv"));
      log.info("File path of csv file {}",filePath );
      this.csvService.dataToCSVFile(filePath, reportRows);

    } catch (IOException e) {
      log.error("Error while creating file {}", e.getMessage());
      throw new RuntimeException(e);
    }

    return ReportResponse.builder().conversationId(conversationId).status("Success")
        .errorMessage("").build();
  }

  public ResponseEntity<ByteArrayResource> buildZipResponse(final File folder, final String file)
      throws IOException {
    final String contentType = "application/octet-stream";
    final List<String> filePaths = new ArrayList<>();
    final String[] files = folder.list();
    log.info("File in buildZipResponse {}", file);
    if (files == null || files.length == 0) {
      throw new CustomException(ErrorCodes.FOLDER_CONTENT_EMPTY, HttpStatus.BAD_REQUEST);
    }
    for (final String content : files) {
      filePaths.add(folder.getAbsolutePath() + File.separator + content);
    }
    final byte[] data = this.buildZipFile(filePaths, file);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + file + DateUtil.getTimestamp() + ".zip" + "\"")
        .contentLength(data.length).body(new ByteArrayResource(data));
  }

  public byte[] buildZipFile(final List<String> filePaths, final String name) throws IOException {
    log.debug("ZIP File has been created and for the report: {}", name);
    final String zipFile =
        this.storageLocation + File.separator + Constants.SUMMARY_DETAILS_FOLDER + File.separator
            + name + DateUtil.getTimestamp() + ".zip";
    final String[] srcFiles = filePaths.toArray(new String[0]);
    final byte[] buffer = new byte[1024];
    try (final FileOutputStream fos = new FileOutputStream(zipFile);
        final ZipOutputStream zos = new ZipOutputStream(fos)) {
      for (final String file : srcFiles) {
        final File srcFile = new File(file);
        try (final FileInputStream fis = new FileInputStream(srcFile)) {
          zos.putNextEntry(new ZipEntry(srcFile.getName()));
          int length;
          while ((length = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
          }
          zos.closeEntry();
        }
        Files.delete(Paths.get(file));
      }
    }
    final Path path = Paths.get(zipFile);
    final byte[] data = Files.readAllBytes(path);
    Files.delete(path);
    return data;
  }

  public SummaryReportMetric buildHeaderSummaryReport(final HeaderSummaryDoc headerSummary,
      final String conversationId) {
    log.info("Inside buildHeaderSummaryReport");
    Optional<SummaryDoc> summaryDoc = this.summaryRuntimeDao.getSummaryDoc(conversationId);
    log.info("SummaryDoc in buildHeaderSummaryReport, conversationId {}", summaryDoc.get().getConversationId());
    return SummaryReportMetric.builder().conversationId(conversationId).headerOrIntent("Header")
        .timestamp(DateUtil.getTimestamp()).sysGenSummary(headerSummary.getPlainText())
        .agentSummary(headerSummary.getPlainText())
        .countOfEntities(String.valueOf(summaryDoc.get().getEntities().size()))
        .countOfVariables(String.valueOf(summaryDoc.get().getVariables().size())).build();
  }

  public SummaryReportMetric buildReportWithIntent(final IntentSummaryDoc intent,
      final String conversationId) {
    log.info("Inside buildReportWithIntent");
    Optional<SummaryDoc> summaryDoc = this.summaryRuntimeDao.getSummaryDoc(conversationId);
    log.info("SummaryDoc in buildReportWithIntent, conversationId {}", summaryDoc.get().getConversationId());
    return SummaryReportMetric.builder().conversationId(conversationId).headerOrIntent("Intent")
        .intentId(intent.getIntentId()).timestamp(DateUtil.getTimestamp())
        .sysGenSummary(intent.getPlainText()).agentSummary(intent.getPlainText())
        .countOfEntities(String.valueOf(summaryDoc.get().getEntities().size()))
        .countOfVariables(String.valueOf(summaryDoc.get().getVariables().size()))
        .disposition(intent.getDisposition()).source(intent.getSource()).build();
  }
}
