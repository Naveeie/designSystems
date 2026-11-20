package com.uniphore.uassist.summary.helper;

import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.dao.SummaryRuntimeDao;
import com.uniphore.uassist.summary.dao.documents.HeaderSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.IntentSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.service.CSVService;
import com.uniphore.uassist.summary.util.DateUtil;
import com.uniphore.uassist.summary.util.ReportResponse;
import com.uniphore.uassist.summary.util.SummaryReportMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class MetricHelperTest {

  @InjectMocks
  private MetricHelper metricHelper;

  @Mock
  private SummaryRuntimeDao summaryRuntimeDao;

  @Mock
  private CSVService csvService;

  @Mock
  private DateUtil dateUtil;

  @Value("${summary.csv.upload-path}")
  private String storageLocation;

  private static final String CONVERSATION_ID = "test_conversation_id";
  private static final String PATH = "test_path";

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testBuildSummaryReportSuccess() throws IOException {
    HeaderSummaryDoc headerSummaryDoc = new HeaderSummaryDoc();
    headerSummaryDoc.setPlainText("Header summary text");

    IntentSummaryDoc intentSummaryDoc = new IntentSummaryDoc();
    intentSummaryDoc.setPlainText("Intent summary text");

    Map<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
    intentSummaries.put("intent1", intentSummaryDoc);
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    SummaryDoc.Slot slot =
        SummaryDoc.Slot.builder().id("slotId").instanceId("instanceId").value("values").build();
    entities.put("Slot1", slot);

    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setHeaderSummary(headerSummaryDoc);
    summaryDoc.setIntentSummaries(intentSummaries);
    summaryDoc.setEntities(entities);
    summaryDoc.setVariables(entities);

    when(summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(Optional.of(summaryDoc));

    ReportResponse response = metricHelper.buildSummaryReport(CONVERSATION_ID, PATH);

    assertNotNull(response);
    assertEquals("Success", response.getStatus());
    verify(csvService, times(1)).dataToCSVFile(any(String.class), anyList());
  }

  @Test
  void testBuildSummaryReportFailure() {
    when(summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(Optional.empty());

    ReportResponse response = metricHelper.buildSummaryReport(null, PATH);

    assertNotNull(response);
  }

//  @Test
//  void testBuildZipResponseSuccess() throws IOException {
//    File folder = mock(File.class);
//    when(folder.list()).thenReturn(new String[]{"file1", "file2"});
//    when(folder.getAbsolutePath()).thenReturn("test_folder");
//    String zipFilePath = storageLocation + File.separator + Constants.SUMMARY_DETAILS_FOLDER + File.separator + "testReport" + "20240808" + ".zip";
//
//    byte[] zipData = new byte[]{1, 2, 3, 4};
//
//    ResponseEntity<ByteArrayResource> response = metricHelper.buildZipResponse(folder, "testFile");
//
//    assertNotNull(response);
//    assertEquals(HttpStatus.OK, response.getStatusCode());
//    assertEquals(zipData.length, Objects.requireNonNull(response.getBody()).getByteArray().length);
//  }

  @Test
  void testBuildZipResponseFolderEmpty() {
    File folder = mock(File.class);
    when(folder.list()).thenReturn(new String[]{});

    CustomException exception = assertThrows(CustomException.class, () -> metricHelper.buildZipResponse(folder, "testFile"));
    assertEquals(ErrorCodes.FOLDER_CONTENT_EMPTY, exception.getCode());
  }

//  @Test
//  public void testBuildZipFile_Success() throws IOException {
//    List<String> filePaths = Arrays.asList("file1.txt", "file2.txt");
//    String name = "testReport";
//    String timestamp = "20240808";
//    String zipFilePath = this.storageLocation + File.separator + Constants.SUMMARY_DETAILS_FOLDER + File.separator + name + timestamp + ".zip";
//
//
//    try (MockedStatic<Files> mockedFiles = mockStatic(Files.class);
//        MockedStatic<Paths> mockedPaths = mockStatic(Paths.class)) {
//
//      Path path = mock(Path.class);
//      mockedPaths.when(() -> Paths.get(zipFilePath)).thenReturn(path);
//      mockedPaths.when(() -> Paths.get("file1.txt")).thenReturn(path);
//      mockedPaths.when(() -> Paths.get("file2.txt")).thenReturn(path);
//
//      byte[] expectedData = new byte[]{1, 2, 3};
//      mockedFiles.when(() -> Files.readAllBytes(path)).thenReturn(expectedData);
//
//      byte[] result = metricHelper.buildZipFile(filePaths, name);
//
//      assertArrayEquals(expectedData, result);
//
//      mockedFiles.verify(() -> Files.delete(path), times(3));
//    }
//  }
  @Test
  void testBuildHeaderSummaryReport() {
    HeaderSummaryDoc headerSummaryDoc = new HeaderSummaryDoc();
    headerSummaryDoc.setPlainText("Header summary text");

    IntentSummaryDoc intentSummaryDoc = new IntentSummaryDoc();
    intentSummaryDoc.setPlainText("Intent summary text");

    Map<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
    intentSummaries.put("intent1", intentSummaryDoc);
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    SummaryDoc.Slot slot =
        SummaryDoc.Slot.builder().id("slotId").instanceId("instanceId").value("values").build();
    entities.put("Slot1", slot);

    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setHeaderSummary(headerSummaryDoc);
    summaryDoc.setIntentSummaries(intentSummaries);
    summaryDoc.setEntities(entities);
    summaryDoc.setVariables(entities);


    when(summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(Optional.of(summaryDoc));

    SummaryReportMetric result = metricHelper.buildHeaderSummaryReport(headerSummaryDoc, CONVERSATION_ID);

    assertNotNull(result);
    assertEquals(CONVERSATION_ID, result.getConversationId());
    assertEquals("Header", result.getHeaderOrIntent());
  }

  @Test
  void testBuildReportWithIntent() {
    HeaderSummaryDoc headerSummaryDoc = new HeaderSummaryDoc();
    headerSummaryDoc.setPlainText("Header summary text");

    IntentSummaryDoc intentSummaryDoc = new IntentSummaryDoc();
    intentSummaryDoc.setPlainText("Intent summary text");

    Map<String, IntentSummaryDoc> intentSummaries = new HashMap<>();
    intentSummaries.put("intent1", intentSummaryDoc);
    Map<String, SummaryDoc.Slot> entities = new HashMap<>();
    SummaryDoc.Slot slot =
        SummaryDoc.Slot.builder().id("slotId").instanceId("instanceId").value("values").build();
    entities.put("Slot1", slot);

    SummaryDoc summaryDoc = new SummaryDoc();
    summaryDoc.setHeaderSummary(headerSummaryDoc);
    summaryDoc.setIntentSummaries(intentSummaries);
    summaryDoc.setEntities(entities);
    summaryDoc.setVariables(entities);
    when(summaryRuntimeDao.getSummaryDoc(CONVERSATION_ID)).thenReturn(Optional.of(summaryDoc));

    SummaryReportMetric result = metricHelper.buildReportWithIntent(intentSummaryDoc, CONVERSATION_ID);

    assertNotNull(result);
    assertEquals(CONVERSATION_ID, result.getConversationId());
    assertEquals("Intent", result.getHeaderOrIntent());
  }
}
