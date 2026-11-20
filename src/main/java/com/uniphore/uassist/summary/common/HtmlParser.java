package com.uniphore.uassist.summary.common;

import com.uniphore.uassist.summary.execption.CustomException;
import com.uniphore.uassist.summary.execption.ErrorCodes;
import com.uniphore.uassist.summary.model.HtmlExtractData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class  HtmlParser {

  public static final String ENTITY = "ENTITY";
  public static final String VARIABLE = "VARIABLE";
  public static final String ENTITY_NAME_TAG = "span.uniphore-entity-name";
  public static final String VARIABLE_NAME_TAG = "span.uniphore-variable-name";
  public static final String ENTITY_VALUE_TAG = "uniphore-entity-value";
  public static final String VARIABLE_VALUE_TAG = "uniphore-variable-value";
  public static final String ENTITY_ID = "entity-id";
  public static final String VARIABLE_ID = "variable-id";
  public static final String CLASS = "class";

  private static Document validate(String html) {
    Parser parser = Parser.htmlParser().setTrackErrors(10);
    Document document = Jsoup.parse(html, "", parser);
    if (!parser.getErrors().isEmpty()) {
      throw new CustomException(ErrorCodes.HTML_CONTENT_INVALID, HttpStatus.BAD_REQUEST);
    }
    return document;
  }

  public static List<HtmlExtractData> extract(String html) {
    return getHtmlExtractData(html, "name");
  }

  private static List<HtmlExtractData> getHtmlExtractData(String html, String typeOfExtraction) {
    List<HtmlExtractData> data = new ArrayList<HtmlExtractData>();
    Document document = HtmlParser.validate(html);
    document.select(typeOfExtraction.equalsIgnoreCase("name") ? ENTITY_NAME_TAG : "span."+ENTITY_VALUE_TAG)
        .forEach(x -> {
          if (StringUtils.isNotEmpty(x.attr(ENTITY_ID))) {
            data.add(new HtmlExtractData(x.attr(ENTITY_ID), x.text(), ENTITY));
          } else {
            throw new CustomException(ErrorCodes.ENTITY_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
          }
        });
    document.select(
            typeOfExtraction.equalsIgnoreCase("name") ? VARIABLE_NAME_TAG : "span."+VARIABLE_VALUE_TAG)
        .forEach(x -> {
          if (StringUtils.isNotEmpty(x.attr(VARIABLE_ID))) {
            data.add(new HtmlExtractData(x.attr(VARIABLE_ID), x.text(), VARIABLE));
          } else {
            throw new CustomException(ErrorCodes.VARIABLE_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
          }
        });
    document.select(typeOfExtraction.equalsIgnoreCase("value") ? ENTITY_NAME_TAG : "span."+ENTITY_VALUE_TAG)
        .forEach(x -> {
          if (StringUtils.isNotEmpty(x.attr(ENTITY_ID))) {
            data.add(new HtmlExtractData(x.attr(ENTITY_ID), x.text(), ENTITY));
          } else {
            throw new CustomException(ErrorCodes.ENTITY_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
          }
        });
    document.select(
            typeOfExtraction.equalsIgnoreCase("value") ? VARIABLE_NAME_TAG : "span."+VARIABLE_VALUE_TAG)
        .forEach(x -> {
          if (StringUtils.isNotEmpty(x.attr(VARIABLE_ID))) {
            data.add(new HtmlExtractData(x.attr(VARIABLE_ID), x.text(), VARIABLE));
          } else {
            throw new CustomException(ErrorCodes.VARIABLE_ID_NOT_EMPTY, HttpStatus.BAD_REQUEST);
          }
        });
    return data;
  }

  public static List<HtmlExtractData> extractValue(String html) {
    return getHtmlExtractData(html, "value");
  }

  public static String setDetectedData(String html, String type, String id, String detectedValue) {
    log.debug("updating detected value: html: {}, type: {}, id: {}, detected value:{} ", html, type,
        id, detectedValue);

    if (detectedValue == null) {
      return html;
    }

    Document document = Jsoup.parse(html);
    if (ENTITY.equals(type)) {
      document.select(
          ENTITY_NAME_TAG + "[" + ENTITY_ID + "=" + id + "], span." + ENTITY_VALUE_TAG + "["
              + ENTITY_ID + "=" + id + "]").forEach(x -> x.text(detectedValue));
      document.select(ENTITY_NAME_TAG + "[" + ENTITY_ID + "=" + id + "]")
          .attr(CLASS, ENTITY_VALUE_TAG);
    } else if (VARIABLE.equals(type)) {
      document.select(
          VARIABLE_NAME_TAG + "[" + VARIABLE_ID + "=" + id + "] , span." + VARIABLE_VALUE_TAG + "["
              + VARIABLE_ID + "=" + id + "]").forEach(x -> x.text(detectedValue));
      document.select(VARIABLE_NAME_TAG + "[" + VARIABLE_ID + "=" + id + "]")
          .attr(CLASS, VARIABLE_VALUE_TAG);
    }
    return document.body().html();
  }

  // to update the intent templates
  public static String setDetectedData(String html, String type, String id, String detectedValue,
      String instanceId) {
    log.debug(
        "updating detected value: html: {}, type: {}, id: {}, detected value:{} instanceid: {}",
        html, type, id, detectedValue, instanceId);

    if (detectedValue == null) {
      return html;
    }

    Document document = Jsoup.parse(html);
    if (ENTITY.equals(type)) {
      Elements selectedElements = document.select(
          ENTITY_NAME_TAG + "[" + ENTITY_ID + "=" + id + "], span." + ENTITY_VALUE_TAG + "["
              + ENTITY_ID + "=" + id + "]");
      log.debug("updating entityid: {} for elements: {}, instanceId: {}", id,
          selectedElements.toString(), instanceId);
      selectedElements.forEach(x -> x.text(detectedValue));
      document.select(ENTITY_NAME_TAG + "[" + ENTITY_ID + "=" + id + "]")
          .attr(CLASS, ENTITY_VALUE_TAG);
    } else if (VARIABLE.equals(type)) {
      Elements select = document.select(
          VARIABLE_NAME_TAG + "[" + VARIABLE_ID + "=" + id + "] , span." + VARIABLE_VALUE_TAG + "["
              + VARIABLE_ID + "=" + id + "]");
      log.debug("updating variableId: {} for elements: {}, instanceId: {}", id, select.toString(),
          instanceId);
      select.forEach(x -> x.text(detectedValue));
      document.select(VARIABLE_NAME_TAG + "[" + VARIABLE_ID + "=" + id + "]")
          .attr(CLASS, VARIABLE_VALUE_TAG);
    }
    return document.body().html();
  }

  public static String setDefaultData(String html, String defaultData) {
    if (StringUtils.isEmpty(html)) {
      return StringUtils.EMPTY;
    }
    Document document = Jsoup.parse(html);
    document.select(ENTITY_NAME_TAG).forEach(x -> x.text(defaultData));
    document.select(VARIABLE_NAME_TAG).forEach(x -> x.text(defaultData));
    return document.body().html();
  }

  public static String extractText(String html) {
    if (StringUtils.isEmpty(html)) {
      return StringUtils.EMPTY;
    }
    List<HtmlExtractData> data = HtmlParser.extract(html);
    Document document = HtmlParser.validate(html);
    for (HtmlExtractData extractData : data) {

      if (ENTITY.equals(extractData.getType())) {
        document.select(ENTITY_NAME_TAG + "[" + ENTITY_ID + "=" + extractData.getId() + "]")
            .forEach(x -> {
              x.text("@" + extractData.getName());
            });
      } else {

        document.select(VARIABLE_NAME_TAG + "[" + VARIABLE_ID + "=" + extractData.getId() + "]")
            .forEach(x -> {
              x.text("@" + extractData.getName());
            });
      }
    }
    return document.text();
  }

  public static String extractPlainText(String html) {
    if (StringUtils.isEmpty(html)) {
      return StringUtils.EMPTY;
    }
    List<HtmlExtractData> data = HtmlParser.extract(html);
    Document document = HtmlParser.validate(html);
    for (HtmlExtractData extractData : data) {

      if (ENTITY.equals(extractData.getType())) {
        document.select(String.format("%s[%s=%s]", ENTITY_NAME_TAG, ENTITY_ID, extractData.getId()))
            .forEach(x -> {
              x.text(extractData.getName());
            });
      } else {
        document.select(
                String.format("%s[%s=%s]", VARIABLE_NAME_TAG, VARIABLE_ID, extractData.getId()))
            .forEach(x -> {
              x.text(extractData.getName());
            });
      }
    }
    log.trace(
        "Convert Detected data to plain text input html: {}, document text as plainText: {}, document body text as plainText: {}, document html: {},document body html: {}",
        html, document.text(), document.body().text(), document.html(), document.body().html());
    return HtmlParser.excludeCharacters(document.body().text());
  }

  public static String excludeCharacters(String input) {
    CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder();
    StringBuilder filteredText = new StringBuilder();

    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (encoder.canEncode(c)) {
        filteredText.append(c);
      }
    }
    log.trace("UTF-8 format filteredPlainText", filteredText.toString());
    return filteredText.toString();
  }

  public static List<String> entORVarIds(List<HtmlExtractData> extractedList, String type) {
    List<String> entORVarIds =
        extractedList.stream().filter(x -> x.getType().equals(type)).map(HtmlExtractData::getId)
            .collect(Collectors.toList());
    return entORVarIds;
  }

}
