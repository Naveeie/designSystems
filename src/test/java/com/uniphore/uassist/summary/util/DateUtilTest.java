package com.uniphore.uassist.summary.util;

import com.google.protobuf.Timestamp;
import com.uniphore.uassist.summary.dao.documents.TimeStampDoc;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilTest {

  @Test
  void testGetTimestamp() {
    String timestamp = DateUtil.getTimestamp();
    assertNotNull(timestamp, "Timestamp should not be null");
    assertTrue(timestamp.matches("\\d+"), "Timestamp should be numeric");
  }

  @Test
  void testGetCurrentTimestamp() {
    Timestamp googleTimestamp = DateUtil.getCurrentTimestamp();
    Instant now = Instant.now();
    assertEquals(now.getEpochSecond(), googleTimestamp.getSeconds(), "Seconds should match");
  }

  @Test
  void testConvertTimestamp() {
    long currentMillis = Instant.now().toEpochMilli();
    Timestamp googleTimestamp = DateUtil.convertTimestamp(currentMillis);
    Instant instant = Instant.ofEpochMilli(currentMillis);
    assertEquals(instant.getEpochSecond(), googleTimestamp.getSeconds(), "Seconds should match");
    assertEquals(instant.getNano(), googleTimestamp.getNanos(), "Nanos should match");
  }

  @Test
  void testGetTimestampFromTimeStampDoc() {
    TimeStampDoc doc = TimeStampDoc.builder().seconds(Instant.now().getEpochSecond())
        .nanos(Instant.now().getNano()).build();
    Instant instant = DateUtil.getTimestamp(doc);
    assertEquals(doc.getSeconds(), instant.getEpochSecond(), "Seconds should match");
  }

  @Test
  void testToGoogleTimestamp() {
    TimeStampDoc doc = TimeStampDoc.builder().seconds(Instant.now().getEpochSecond())
        .nanos(Instant.now().getNano()).build();
    Timestamp googleTimestamp = DateUtil.toGoogleTimestamp(doc);
    assertEquals(doc.getSeconds(), googleTimestamp.getSeconds(), "Seconds should match");
    assertEquals(doc.getNanos(), googleTimestamp.getNanos(), "Nanos should match");
  }

  @Test
  void testToTimestamp() {
    Timestamp googleTimestamp = Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond())
        .setNanos(Instant.now().getNano()).build();
    TimeStampDoc doc = DateUtil.toTimestamp(googleTimestamp);
    assertEquals(googleTimestamp.getSeconds(), doc.getSeconds(), "Seconds should match");
    assertEquals(googleTimestamp.getNanos(), doc.getNanos(), "Nanos should match");
  }
}
