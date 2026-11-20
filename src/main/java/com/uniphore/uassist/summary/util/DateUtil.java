package com.uniphore.uassist.summary.util;

import com.uniphore.uassist.summary.dao.documents.TimeStampDoc;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;

@Slf4j
public class DateUtil {

  private DateUtil() {}

  public static final Clock TIME = Clock.systemDefaultZone();

  public static String getTimestamp() {
    return String.valueOf(DateUtil.TIME.millis());
  }

  public static com.google.protobuf.Timestamp getCurrentTimestamp() {
    Instant instant = Instant.now();
    return com.google.protobuf.Timestamp.newBuilder().setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano()).build();
  }

  public static com.google.protobuf.Timestamp convertTimestamp(long timestamp) {
    Instant instant = Instant.ofEpochMilli(timestamp);
    return com.google.protobuf.Timestamp.newBuilder().setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano()).build();
  }

  public static Instant getTimestamp(TimeStampDoc timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds());
  }

  public static com.google.protobuf.Timestamp toGoogleTimestamp(TimeStampDoc timestamp) {
    return com.google.protobuf.Timestamp.newBuilder().setSeconds(timestamp.getSeconds())
        .setNanos(timestamp.getNanos()).build();
  }

  public static TimeStampDoc toTimestamp(com.google.protobuf.Timestamp timestamp) {
    return TimeStampDoc.builder().seconds(timestamp.getSeconds()).nanos(timestamp.getNanos())
        .build();
  }



}
