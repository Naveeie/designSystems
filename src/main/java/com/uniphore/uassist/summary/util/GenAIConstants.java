package com.uniphore.uassist.summary.util;

import java.util.regex.Pattern;

public final class GenAIConstants {
  private GenAIConstants() {
    // no instances please!
  }

  public static final String PARAGRAPH = "paragraph";
  public static final String BULLET_POINT = "bullet-point";
  public static final String BULLET_POINT_CHARACTER = "\u2022 ";
  public static final String NEWLINE_BULLET = "\n\u2022 ";
  public static final String NUMBER_LIST = "number-list";
  public static final Pattern PATTERN = Pattern.compile("\\. <n>");
  public static final String DOT_NEW_LINE = ".<n>";
  public static final String HTML_TAG_PATTERN = "<(?!n>)[^>]+>";
  public static final Pattern ENTITY_PATTERN =
      Pattern.compile("The following entities were not found: ([^\\.]+)\\.");
  public static final Pattern UNFORMATTED_ENTITY_PATTERN =
      Pattern.compile("The following entities were not found: ([^<]+)<n>");
  public static final String SENTENCE_BOUNDARY_REGEX = ".*[.?!]$";
  public static final String NOT_SENTENCE_END_REGEX = "(?s).*(?:\\d|\\b\\p{Lu}\\p{L}{0,4})\\.$";
  public static final String GEN_AI_PATTERN = "<n>";
  public static final String DOT_NEW_LINE_SPACE = ".<n> ";

}
