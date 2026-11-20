package com.uniphore.uassist.summary.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class CSVService {

  public String dataToCSVFile(final String filePath, final List<String[]> dataLines)
      throws IOException {

    final File csvOutputFile = new File(filePath);
    csvOutputFile.getParentFile().mkdirs();
    try (OutputStream os = new FileOutputStream(csvOutputFile);
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));) {
      os.write(239);
      os.write(187);
      os.write(191);
      dataLines.stream().map(this::convertToCSV).forEach(pw::println);
    } catch (Exception e) {
      log.error("Error while adding data to csv file {}", e.getMessage());
    }
    return csvOutputFile.getAbsolutePath();
  }

  private String convertToCSV(final String[] data) {
    return Stream.of(data).map(this::escapeSpecialCharacters).collect(Collectors.joining(","));
  }

  private String escapeSpecialCharacters(String data) {
    String escapedData = data.replaceAll("\\R", " ");
    if (data.contains(",") || data.contains("\"") || data.contains("'")) {
      data = data.replace("\"", "\"\"");
      escapedData = "\"" + data + "\"";
    }
    return escapedData;
  }
  
}
