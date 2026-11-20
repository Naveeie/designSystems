package com.uniphore.uassist.summary.util;

import com.uniphore.conversationmetric.v1.MetricScope;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.llminference.v1.CreateGenerativeSummaryResponse;
import com.uniphore.llminference.v1.PresentationFormat;
import com.uniphore.llminference.v1.Section;
import com.uniphore.llminference.v1.SectionText;
import com.uniphore.summary.v1.Summary;
import com.uniphore.uassist.summary.cache.GenAISummaryCache;
import com.uniphore.uassist.summary.common.Constants;
import com.uniphore.uassist.summary.common.CustomMetricsPublisher;
import com.uniphore.uassist.summary.dao.documents.GenAiSummaryDoc;
import com.uniphore.uassist.summary.dao.documents.SummaryDoc;
import com.uniphore.uassist.summary.helper.LookerGenAISummaryMetricHelper;
import com.uniphore.uassist.summary.model.ProtoConverter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uniphore.featureflag.LaunchDarklyConstants.*;
import static com.uniphore.uassist.summary.common.Constants.*;
import static com.uniphore.uassist.summary.util.GenAIConstants.*;

@Component
@Slf4j
public class GenAiSummaryHelper {

  private static final String DOT = ".";
  private static final String DOT_SPACE = ".";
  private static final String REGEX_DOT = "\\.";
  private static final String REGEX_DOT_SPACE = "\\. ";
  private static final String STREAM_BULLET_REPLACEMENT = ".<n>\n<n> \u2022";
  private static final String STREAM_NUMBER_LIST_REPLACEMENT = ".<n>\n<n>%d. ";
  private static final String START_OF_BULLET_REPLACEMENT = "<n>\u2022 ";
  private static final String EOF = "<eof>";
  private static final String REGEX_SENTENCE_END = "(?<!\\d)\\.(?!\\d)";
  private static final String DOUBLE_NEW_LINE = "\n\n";
  private static final String DOT_SINGLE_SPACE = ". ";

  private static final int MAX_BUFFER_SIZE = 9;
  // string replace placeholders
  private static final String STR_STR = "%s%s";
  private static final String STR_TAG_N_STR = "%s<n>%s";
  private static final String TAG_N_NUM_DOT = "<n>%d. ";
  private static final String TAG_N = "<n>";
  private boolean startNewItem = false;
  private int numberCounter = 1;

  private static final List<String> SECTIONS =
      List.of(Constants.CALL_REASON, Constants.AGENT_ACTIONS, Constants.CALL_OUTCOME,
          Constants.CUSTOMER_SENTIMENT);

  @Autowired
  private CustomMetricsPublisher customMetricsPublisher;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  @Autowired
  private SummaryHelper summaryHelper;

  @Autowired
  private LookerGenAISummaryMetricHelper metricHelper;

  private Map<String, Map<String, String>> genAIConvSectionMap;
  private Map<String,String> sectionConvMap;
  private Map<String,Integer> numberCounterMap;

  private Map<String, PresentationFormat> presentationFormatMap;

  @PostConstruct
  void init() {
    // Shared across concurrent GenAI-summary executor threads (one per conversation), so these
    // must be thread-safe; a plain HashMap throws ConcurrentModificationException under load.
    this.genAIConvSectionMap = new ConcurrentHashMap<>();
    this.presentationFormatMap = new ConcurrentHashMap<>();
    this.sectionConvMap = new ConcurrentHashMap<>();
    this.numberCounterMap = new ConcurrentHashMap<>();
  }

  public GenAiSummaryDoc formattedSummaryDoc(String conversationId,
      Optional<CreateGenerativeSummaryResponse> getAISummaryFromModelOpt, GenAISummaryCache config,
      SummaryDoc summaryDoc) {
    log.debug("llm summary response: conversationId: {}, response: {}", conversationId,
        getAISummaryFromModelOpt);
    GenAiSummaryDoc genAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    if (getAISummaryFromModelOpt.isPresent()) {
      genAiSummary = this.formatGenAISummary(getAISummaryFromModelOpt.get(), config, summaryDoc);
      log.debug("createGenAiSummary for conversationId: {} summary: {}", conversationId,
          genAiSummary);
    } else {
      log.info("handleTranscriptAddedEvent::genAI summary generated empty for conversation id: {}",
          conversationId);
    }
    return genAiSummary;
  }

  private static String getFormatType(final PresentationFormat presentationFormat) {
    switch (presentationFormat) {
      case PRESENTATION_FORMAT_PARAGRAPH:
        return PARAGRAPH;
      case PRESENTATION_FORMAT_BULLET:
        return BULLET_POINT;
      case PRESENTATION_FORMAT_NUMERIC:
      default:
        return NUMBER_LIST;
    }
  }

  GenAiSummaryDoc formatGenAISummary(final CreateGenerativeSummaryResponse response,
      final GenAISummaryCache config, final SummaryDoc summaryDoc) {
    boolean genAiEntitiesEnhancementsEnabled =
        this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_ENHANCEMENTS, FF_CONTEXT_KIND);
    log.debug("GenAI entities enhancements enabled: {}", genAiEntitiesEnhancementsEnabled);
    GenAiSummaryDoc genAiSummaryDoc = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    GenAiSummaryDoc.Section sectionDocUnformatted =
        GenAiSummaryDoc.Section.builder().id(Constants.SUMMARY).text(StringUtils.EMPTY).build();
    boolean genAiEntitiesToLLMEnabled =
        this.ldHelper.isFFEnabled(UASSIST_GEN_AI_ENTITIES_TO_LLM, FF_CONTEXT_KIND);
    Map<String, Set<String>> unmappedVariableInGenAiSummaries = new HashMap<>();
    Set<String> formattedEntitySet = new HashSet<>();

    if (!this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
        FF_CONTEXT_KIND) || StringUtils.isBlank(summaryDoc.getTemplateId())) {
      response.getSectionTextList().forEach(s -> {
        GenAiSummaryDoc.Section sectionDoc = GenAiSummaryDoc.Section.builder().build();
        Section section = s.getSection();
        if (config.isFormatEnabled()) {
          sectionDoc.setId(section.getId());
          String result = this.formatSummaryTextByUserConfig(config, s.getText(), section.getId());
          sectionDoc.setText(result);
          log.debug("Formatted section text: {} and sectionId is: {}", result, section.getId());
          if (genAiEntitiesEnhancementsEnabled && config.getSectionMap() != null) {
            if (config.getSectionMap().containsKey(section.getId())) {
              List<GenAiSummaryDoc.SpokenEntity> spokenEntities =
                  convertSpokenEntities(config.getSectionMap().get(section.getId()),
                      summaryDoc.getVariables());
              sectionDoc.setSpokenEntities(spokenEntities);
              if (genAiEntitiesToLLMEnabled) {
                String updatedText =
                    this.removeAndBuildUndetectedSpokenEntity(result, formattedEntitySet,
                        section.getId(), config);
                sectionDoc.setText(getText(updatedText));
              }
              log.debug("Added spoken entities to section: {}", spokenEntities);
            } else if (genAiEntitiesToLLMEnabled) {
              String text =
                  result.replaceAll(REMOVE_TAG, String.format("%s$1%s", BOLD_OPEN, BOLD_CLOSE));
              sectionDoc.setText(String.format("%s%s%s", PARAGRAPH_OPEN, text, PARAGRAPH_CLOSE));
            }
            if (!formattedEntitySet.isEmpty()) {
              unmappedVariableInGenAiSummaries.put(s.getSection().getId(), formattedEntitySet);
            }
          }
          genAiSummaryDoc.getSections().add(sectionDoc);
        } else {
          log.debug("1. SectionId:= {}, sectionText:= {}", section.getId(), s.getText());
          if (section.getId().equalsIgnoreCase(Constants.CUSTOMER_SENTIMENT)) {
            sectionDoc.setId(section.getId());
            if (genAiEntitiesToLLMEnabled) {
              sectionDoc.setText(String.format(GEN_AI_SECTION_PATTERN, s.getText()));
            } else {
              sectionDoc.setText(String.format(GEN_AI_PATTERN_TEXT, s.getText()));
            }
            genAiSummaryDoc.getSections().add(sectionDoc);
          } else {
            String text = getUnformattedText(s, sectionDocUnformatted, section);
            sectionDocUnformatted.setText(text);
            if (genAiEntitiesEnhancementsEnabled && config.getSectionMap() != null)
              addSpokenEntities(config, summaryDoc, sectionDocUnformatted,
                  genAiEntitiesToLLMEnabled, text, section, unmappedVariableInGenAiSummaries);
          }
        }
      });
      Set<String> commonValues = findCommonValues(unmappedVariableInGenAiSummaries);
      if (!commonValues.isEmpty()) {
        log.debug("Spoken entities:= {} added to unmappedVariableInGenAiSummaries:= {}",
            sectionDocUnformatted.getSpokenEntities(), commonValues);
        sectionDocUnformatted.getSpokenEntities().forEach(e -> {
          if (commonValues.contains(e.getLabel())) {
            e.setLabelUnmentioned(true);
          }
        });
      }
      if (StringUtils.isNotEmpty(sectionDocUnformatted.getText())) {
        genAiSummaryDoc.getSections().add(sectionDocUnformatted);
      }
      if (!unmappedVariableInGenAiSummaries.isEmpty()) {
        genAiSummaryDoc.setUnmappedVariableInGenAiSummaries(
            this.aggregateMaps(List.of(unmappedVariableInGenAiSummaries)));
      } else if (formattedEntitySet.isEmpty()) {
        genAiSummaryDoc.setUnmappedVariableInGenAiSummaries(
            this.aggregateMaps(List.of(unmappedVariableInGenAiSummaries)));
      }
    } else {
      // FF enabled and templateId present, so we directly map sectionId + text
      response.getSectionTextList().forEach(s -> {
        GenAiSummaryDoc.Section sectionDoc = GenAiSummaryDoc.Section.builder().build();
        Section section = s.getSection();

        sectionDoc.setId(section.getId());
        sectionDoc.setText(s.getText());

        genAiSummaryDoc.getSections().add(sectionDoc);
        log.debug("Added section from response . sectionId={}, text={}", section.getId(),
            s.getText());
      });
    }
    log.debug("genAiSummaryDoc:{}", genAiSummaryDoc);
    return genAiSummaryDoc;
  }

  private static String getUnformattedText(SectionText s,
      GenAiSummaryDoc.Section sectionDocUnformatted, Section section) {
    String existingText = sectionDocUnformatted.getText();
    log.debug("2. SectionId:= {}, existingText:= {}", section.getId(), existingText);
    String text =
        String.format("%s%s%s%s%s", existingText, " ", GEN_AI_PATTERN, s.getText(), GEN_AI_PATTERN);
    log.debug("3. SectionId:= {}, text:= {}", section.getId(), text);
    return text;
  }

  private static String getUnformattedTextStream(SectionText s,
      GenAiSummaryDoc.Section sectionDocUnformatted, Section section) {

    String incoming = s.getText();
    String finalText = incoming.replace(NEW_LINE, EMPTY_SPACE);

    log.debug("[STREAM] Incoming word raw: '{}'", incoming);
    log.debug("[STREAM] Incoming word cleaned: '{}'", finalText);

    String existingText = sectionDocUnformatted.getText();
    log.debug("[STREAM] Existing text before update: '{}'", existingText);
    // Always append normally — single paragraph behavior
    String newText =
        existingText.isEmpty() ? finalText : (existingText + EMPTY_SPACE + finalText);

    log.debug("[STREAM] Updated text: '{}'", newText);
    return newText;
  }

  private Set<String> findCommonValues(Map<String, Set<String>> entityMap) {
    log.debug("Entity map: {}", entityMap);
    List<Set<String>> cleanedSets = entityMap.values().stream().map(set -> set.stream()
        .map(value -> value.endsWith(".") ? value.substring(0, value.length() - 1) : value)
        .collect(Collectors.toSet())).collect(Collectors.toList());

    log.debug("cleanedSets:: {}", cleanedSets);
    if (cleanedSets.stream().anyMatch(Set::isEmpty)) {
      return Collections.emptySet();
    }

    return cleanedSets.stream().reduce((set1, set2) -> {
      set1.retainAll(set2);
      return set1;
    }).orElse(Collections.emptySet());
  }

  void addSpokenEntities(GenAISummaryCache config, SummaryDoc summaryDoc,
      GenAiSummaryDoc.Section sectionDocUnformatted, boolean genAiEntitiesToLLMEnabled, String text,
      Section section, Map<String, Set<String>> unmappedVariableInGenAiSummaries) {
    log.debug(
        "Entering addSpokenEntities method with sectionId: {} and genAiEntitiesToLLMEnabled: {}",
        section.getId(), genAiEntitiesToLLMEnabled);

    if (config == null || config.getSectionMap() == null) {
      log.warn("Config or SectionMap is null, exiting addSpokenEntities method");
      return;
    }

    log.debug("Fetching spoken entities for section: {}", sectionDocUnformatted.getId());
    List<GenAiSummaryDoc.SpokenEntity> spokenEntities =
        convertSpokenEntities(config.getSectionMap().get(sectionDocUnformatted.getId()),
            summaryDoc.getVariables());
    sectionDocUnformatted.setSpokenEntities(spokenEntities);
    log.debug("Converted spoken entities: {}", spokenEntities);

    if (genAiEntitiesToLLMEnabled) {
      Set<String> entitySet = new HashSet<>();
      log.debug("Removing and building undetected spoken entities for section: {}",
          sectionDocUnformatted.getId());
      String updatedText =
          this.removeAndBuildUndetectedSpokenEntity(text, entitySet, sectionDocUnformatted.getId(),
              config);
      log.debug("Updated text after removing undetected entities: {}", updatedText);

      String TEXT = addingMetadataForInline(updatedText,
          config.getSectionMap().get(sectionDocUnformatted.getId()), summaryDoc.getVariables());
      log.debug("Processed TEXT value: {}", TEXT);

      String finalText = getText(TEXT);
      log.debug("Final text after adding metadata for inline entities: {}", finalText);

      sectionDocUnformatted.setText(finalText);
      log.debug(
          "EntitySet:: {} and sectionId:: {} unmappedVariableInGenAiSummaries:: {} sectionDocUnformatted:: {}",
          entitySet, section.getId(), unmappedVariableInGenAiSummaries, sectionDocUnformatted);
      unmappedVariableInGenAiSummaries.put(section.getId(), entitySet);
    }
    log.debug("Exiting addSpokenEntities method. Added spoken entities to unformatted section: {}",
        spokenEntities);
  }

  private static String addingMetadataForInline(String text,
      List<GenAISummaryCache.SpokenEntity> spokenEntities, Map<String, SummaryDoc.Slot> variables) {
    log.debug("Entering addingMetadataForInline with text: {}", text);

    Pattern pattern = Pattern.compile(REMOVE_TAG);
    Matcher matcher = pattern.matcher(text);
    StringBuffer formattedText = new StringBuffer();

    while (matcher.find()) {
      String entityName = matcher.group(1);
      log.debug("Processing entity name found inside <>: {}", entityName);

      Optional<GenAISummaryCache.SpokenEntity> entityOpt = spokenEntities.stream().filter(
          e -> e.getLabel().equalsIgnoreCase(entityName) & e.getPlacement()
              .equalsIgnoreCase(GEN_AI_IN_LINE)).findFirst();

      if (entityOpt.isPresent()) {
        GenAISummaryCache.SpokenEntity entity = entityOpt.get();
        SummaryDoc.Slot variable = variables.get(entity.getVariableId());
        log.debug("Matched entity: {} with variable: {}", entity, variable);

        final String replacement = getReplacement(variable, entity);
        log.debug("Replacement text: {}", replacement);
        matcher.appendReplacement(formattedText, replacement);
      } else {
        log.debug("No matching entity found for: {}, keeping original <strong> tag", entityName);
        matcher.appendReplacement(formattedText, matcher.group());
      }
    }
    matcher.appendTail(formattedText);

    log.debug("Exiting addingMetadataForInline with formatted text: {}", formattedText.toString());
    return formattedText.toString();
  }

  private static String getReplacement(SummaryDoc.Slot variable,
      GenAISummaryCache.SpokenEntity entity) {
    log.debug("Entering getReplacement method with entity: {} and variable: {}", entity, variable);

    String variableInstanceId = (variable != null) ? variable.getInstanceId() : EMPTY;
    String variableValue = (variable != null) ? variable.getValue() : EMPTY;

    log.debug("Using variableInstanceId: {} and variableValue: {}", variableInstanceId,
        variableValue);

    String replacement =
        BOLD_OPEN + entity.getLabel() + BOLD_CLOSE_WITH_COLON + String.format(SPAN_WITH_VARIABLE,
            entity.getVariableId(), variableInstanceId, variableValue, variableValue);

    log.debug("Exiting getReplacement with replacement text: {}", replacement);
    return replacement;
  }

  Map<String, Set<String>> aggregateMaps(List<Map<String, Set<String>>> maps) {
    return maps.stream().flatMap(m -> m.entrySet().stream()).flatMap(e -> e.getValue().stream()
            .map(value -> new AbstractMap.SimpleEntry<>(removeTrailingPeriod(value), e.getKey())))
        .filter(entry -> !entry.getKey().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
          Set<String> keySet = new HashSet<>();
          keySet.add(entry.getValue());
          return keySet;
        }, (set1, set2) -> {
          Set<String> mergedSet = new HashSet<>(set1);
          mergedSet.addAll(set2);
          return mergedSet;
        }));
  }

  private List<GenAiSummaryDoc.SpokenEntity> convertSpokenEntities(
      List<GenAISummaryCache.SpokenEntity> spokenEntities, Map<String, SummaryDoc.Slot> variables) {
    log.debug("convertSpokenEntities with spokenEntities : {} and Variables: {}", spokenEntities,
        variables);
    if (spokenEntities == null || variables == null) {
      return Collections.emptyList();
    }
    return spokenEntities.stream().map(spkEntity -> {
      SummaryDoc.Slot variable = variables.get(spkEntity.getVariableId());
      if (variable != null) {
        log.debug("Variable found for spoken entity: {}", spkEntity.getVariableId());
        return GenAiSummaryDoc.SpokenEntity.builder().variableId(variable.getId())
            .placement(spkEntity.getPlacement()).variableInstanceId(variable.getInstanceId())
            .value(variable.getValue()).label(spkEntity.getLabel()).isEdited(variable.isEdited())
            .build();
      } else {
        log.debug("Variable not found for spoken entity: {}", spkEntity.getVariableId());
        return GenAiSummaryDoc.SpokenEntity.builder().variableId(spkEntity.getVariableId())
            .placement(spkEntity.getPlacement()).value("").variableInstanceId("")
            .label(spkEntity.getLabel()).isEdited(false).build();
      }
    }).collect(Collectors.toList());
  }

  String removeAndBuildUndetectedSpokenEntity(final String input, final Set<String> entitySet,
      final String header, GenAISummaryCache config) {
    String formatString = "";
    if (config.getSummaryFormat() != null) {
      formatString = resolveFormatString(config, header);
    }
    Pattern PATTERN_TO_COMPILE =
        StringUtils.isNotEmpty(formatString) ? ENTITY_PATTERN : UNFORMATTED_ENTITY_PATTERN;
    Matcher matcher = PATTERN_TO_COMPILE.matcher(input);
    int lastEnd = 0;
    boolean found = false;
    StringBuilder modifiedText = new StringBuilder();
    while (matcher.find()) {
      modifiedText.append(input, lastEnd, matcher.start());
      String[] entities = matcher.group(1).split(",\\s*");
      for (String label : entities) {
        if (!entitySet.contains(label)) {
          entitySet.add(label);
        }
      }
      lastEnd = matcher.end();
      found = true;
    }
    modifiedText.append(input.substring(lastEnd));
    String result = modifiedText.toString();
    result = processEndOfSectionTags(result, header, config);
    if (found) {
      switch (formatString) {
        case PARAGRAPH:
          log.debug("Removing last comma for paragraph:: {}", result);
          if (result.endsWith(NEW_LINE)) {
            result = result.substring(0, result.length() - 2);
          }
          break;
        case NUMBER_LIST:
          log.debug("Removing last comma for number list:: {}", result);
          result = PATTERN.matcher(modifiedText.append(input.substring(lastEnd)).toString())
              .replaceAll(DOT_NEW_LINE).substring(0, result.length() - 14);
          break;
        case BULLET_POINT:
          log.debug("Removing last comma for bullet point:: {}", result);
          result = PATTERN.matcher(modifiedText.append(input.substring(lastEnd)).toString())
              .replaceAll(DOT_NEW_LINE).substring(0, result.length() - 13);
          break;
        default:
          modifiedText.append(GEN_AI_PATTERN);
          log.debug("Removing last comma for default:: {}", result);
          result = PATTERN.matcher(modifiedText.append(input.substring(lastEnd)).toString())
              .replaceAll(DOT_NEW_LINE_SPACE);
          result = processEndOfSectionTags(result, header, config);
          break;
      }
    }

    return result;
  }

  private String processEndOfSectionTags(String input, String header, GenAISummaryCache config) {
    Pattern pattern = Pattern.compile(REMOVE_TAG);
    Matcher matcher = pattern.matcher(input);
    StringBuffer formattedText = new StringBuffer();

    while (matcher.find()) {
      String entityName = matcher.group(1);
      log.debug("Processing entity name found inside <> for eos: {}", entityName);
      List<GenAISummaryCache.SpokenEntity> spokenEntities = config.getSectionMap().get(header);

      Optional<GenAISummaryCache.SpokenEntity> entityOpt = spokenEntities.stream().filter(
          e -> e.getLabel().equalsIgnoreCase(entityName) && (
              GEN_AI_END_OF_SECTION_FOR_DISPLAY.equalsIgnoreCase(e.getPlacement())
                  || GEN_AI_END_OF_SECTION_FOR_DISPLAY_AND_MENTION.equalsIgnoreCase(
                  e.getPlacement()))).findFirst();

      if (entityOpt.isPresent()) {
        String replacement = BOLD_OPEN + entityOpt.get().getLabel() + BOLD_CLOSE;
        log.debug("Replacement text for eos: {}", replacement);
        matcher.appendReplacement(formattedText, replacement);
      } else {
        log.debug("No matching entity found for: {}, keeping original <strong> tag for eos",
            entityName);
        matcher.appendReplacement(formattedText, matcher.group());
      }
    }
    matcher.appendTail(formattedText);

    log.debug("Exiting processEndOfSectionTags with formatted text: {}", formattedText.toString());
    return formattedText.toString();
  }

  private String getText(String result) {
    result = result.replaceAll(DIV_REPLACE, "$1");
    log.debug("Section Text after removing tags: {}", result);
    return String.format("%s%s%s", PARAGRAPH_OPEN, result, PARAGRAPH_CLOSE);
  }

  private String formatTextBasedOnFormatType(final String formatType, String summaryText) {
    String result;
    switch (formatType) {
      case PARAGRAPH:
        List<String> split = Arrays.asList(summaryText.split(GENAI_SUMMARY_FORMAT_PATTERN));
        result = split.stream().map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.joining(NEW_LINE));
        log.debug("paragraph value:{},split value:{},result:{}", summaryText, split, result);
        break;
      case BULLET_POINT:
        split = Arrays.asList(summaryText.split(GENAI_SUMMARY_BULLET_FORMAT));
        StringBuilder bulletPointBuilder = new StringBuilder();
        log.debug("bullet value:{},split value:{}", summaryText, split);
        for (String p : split) {
          String trimmedPart = p.trim();
          if (!trimmedPart.isEmpty()) {
            String formatted =
                String.format("%s \u2022 %s%s", GEN_AI_PATTERN, trimmedPart, GEN_AI_PATTERN);
            if (bulletPointBuilder.length() > 0) {
              bulletPointBuilder.append(NEW_LINE);
            }
            bulletPointBuilder.append(formatted);
          }
        }
        log.debug("bullet point:{}", bulletPointBuilder.toString());
        result = bulletPointBuilder.toString().trim();
        break;
      case NUMBER_LIST:
      default:
        int index = 1;
        split = Arrays.asList(summaryText.split(GENAI_SUMMARY_FORMAT_PATTERN));
        StringBuilder numberListBuilder = new StringBuilder();

        for (String p : split) {
          String trimmedPart = p.trim();
          if (!trimmedPart.isEmpty()) {
            String formatted =
                String.format("%s %d. %s%s", GEN_AI_PATTERN, index, trimmedPart, GEN_AI_PATTERN);
            if (index > 1) {
              numberListBuilder.append(NEW_LINE);
            }
            numberListBuilder.append(formatted);
            index++;
          }
        }
        result = numberListBuilder.toString().trim();
        break;
    }
    log.debug("formatted genAI summary: {}", result);
    return result + " ";
  }

  private String formatTextOnFormatTypeTemplateBased(final String formatType, String summaryText,
      Integer isFirstWord, String textSoFar) {
    String result;
    log.debug("Formatting text. formatType='{}', summaryText='{}', isFirstWord={}", formatType,
        summaryText, isFirstWord);
    boolean sentenceBoundary = summaryText.trim().matches(SENTENCE_BOUNDARY_REGEX);
    switch (formatType) {
      case PARAGRAPH:
        result = formatParagraphWord(summaryText, isFirstWord, sentenceBoundary, textSoFar);
        log.debug("PARAGRAPH: Formatted result='{}'", result);
        break;
      case BULLET_POINT:
        StringBuilder bulletBuilder = new StringBuilder();
        // Start new bullet if it's the first word or flagged
        if ((isFirstWord != null && isFirstWord == 1) || startNewItem) {
          if (isFirstWord != null && isFirstWord == 1) {
            bulletBuilder.append(BULLET_POINT_CHARACTER);
            log.debug("Added first bullet.");
          } else {
            bulletBuilder.append(NEWLINE_BULLET);
            log.debug("Added new bullet after sentence end.");
          }
          startNewItem = false;
        }
        bulletBuilder.append(summaryText);
        log.debug("Appended word: '{}', current bulletBuilder='{}',sentenceBoundary:{}",
            summaryText, bulletBuilder.toString(), sentenceBoundary);
        if (sentenceBoundary) {
          bulletBuilder.append(GEN_AI_PATTERN);  // mark end of sentence
          startNewItem = true;
          log.debug("Word ends sentence. Appended <n> and flagged startNewItem for next bullet.");
        }
        result = bulletBuilder.toString();
        log.debug("BULLET_POINT: Final result='{}'", result);
        break;
      case NUMBER_LIST:
      default:
        if (isFirstWord != null && isFirstWord == 1) {
          numberCounter = 1;
          log.debug("NUMBER_LIST: Reset numberCounter to 1 for new list");
        }
        StringBuilder numberBuilder = new StringBuilder();
        if ((isFirstWord != null && isFirstWord == 1) || startNewItem) {
          if (isFirstWord != null && isFirstWord == 1) {
            numberBuilder.append(numberCounter).append(DOT_SPACE);
            log.debug("Added first number '{}.'", numberCounter);
          } else {
            numberBuilder.append(NEW_LINE).append(numberCounter).append(DOT_SPACE);
            log.debug("Added new number '{}.' after sentence end", numberCounter);
          }
          startNewItem = false;
          numberCounter++;
        }
        numberBuilder.append(summaryText);
        log.debug("Appended word: '{}', current numberBuilder='{}'", summaryText,
            numberBuilder.toString());
        if (sentenceBoundary) {
          numberBuilder.append(GEN_AI_PATTERN);  // mark end of sentence
          startNewItem = true;
          log.debug("Word ends sentence. Appended <n> and flagged startNewItem for next number.");
        }

        result = numberBuilder.toString();
        log.debug("NUMBER_LIST: Final result='{}'", result);
        break;
    }
    log.debug("formatted genAI summary stream: {}", result);
    return result;
  }

  /**
   * Section text accumulated so far for this stream, including the chunk being formatted right now:
   * {@link #persistStreamResponse} runs before the formatter in the same loop iteration. Null when
   * unavailable, in which case callers fall back to the chunk alone.
   */
  private String sectionTextSoFar(String requestId, String sectionId) {
    if (requestId == null) {
      return null;
    }
    Map<String, String> sections = this.genAIConvSectionMap.get(requestId);
    return sections == null ? null : sections.get(sectionId);
  }

  private String formatParagraphWord(String summaryText, Integer isFirstWord,
      boolean sentenceBoundary, String textSoFar) {
    if (isFirstWord != null && isFirstWord == 1) {
      startNewItem = false;
      log.debug("PARAGRAPH: Reset startNewItem for new paragraph list");
    }
    StringBuilder paragraphBuilder = new StringBuilder();
    paragraphBuilder.append(summaryText);
    // A chunk is often just "." with nothing before the dot, so judge it against the section text
    // accumulated so far; fall back to the chunk when that is unavailable.
    String guardText = StringUtils.isBlank(textSoFar) ? summaryText : textSoFar;
    if (sentenceBoundary && !guardText.trim().matches(NOT_SENTENCE_END_REGEX)) {
      paragraphBuilder.append(GEN_AI_PATTERN);
      startNewItem = true;
      log.debug("Word ends sentence. Appended <n> for new paragraph line.");
    }
    return paragraphBuilder.toString();
  }

  String formatSummaryTextByUserConfig(GenAISummaryCache config, String summaryText,
      String header) {
    String formatString = this.resolveFormatString(config, header);
    // to be added new code.
    return formatTextBasedOnFormatType(formatString, summaryText);
  }

  private String resolveFormatString(GenAISummaryCache config, String header) {
    if (config.getSummaryFormat() == null) {
      return PARAGRAPH;
    }
    String formatString = PARAGRAPH;
    if (header.equalsIgnoreCase(Constants.CALL_REASON)) {
      formatString = config.getSummaryFormat().getCallReason();
    } else if (header.equalsIgnoreCase(Constants.AGENT_ACTIONS)) {
      formatString = config.getSummaryFormat().getAgentSteps();
    } else if (header.equalsIgnoreCase(Constants.CALL_OUTCOME)) {
      formatString = config.getSummaryFormat().getOutcome();
    }
    return formatString;
  }

  private static String removeTrailingPeriod(String str) {
    return str.endsWith(".") ? str.substring(0, str.length() - 1) : str;
  }

  /**
   * Publishes a summary event under a fresh, detached trace root so each Kafka
   * emit gets its own trace_id. processStream() emits many chunks inside one
   * upstream trace; the DD Java agent auto-injects that trace-context onto every
   * outbound Kafka record, so downstream consumers inherit the same trace_id and
   * append their spans, causing the trace to balloon past the DD trace-agent
   * msgpack payload cap (~5MB) and be dropped in full.
   *
   * Tracer setup is best-effort: any exception from GlobalTracer / span builder
   * is caught and logged so it never blocks the actual publish. If
   * summaryHelper.publishNoLog itself throws, that exception propagates to the
   * caller as before.
   */
  void publishAsNewTraceRoot(Summary summaryProto, String eventType, String conversationId) {
    Span span = null;
    Scope scope = null;
    try {
      Tracer tracer = GlobalTracer.get();
      span = tracer.buildSpan("uassist.summary.publish")
          .ignoreActiveSpan()
          .withTag("conversationId", conversationId == null ? "" : conversationId)
          .withTag("eventType", eventType == null ? "" : eventType)
          .start();
      scope = tracer.activateSpan(span);
    } catch (Throwable t) {
      log.warn("failed to start detached trace root for eventType {}; publishing without it",
          eventType, t);
    }
    try {
      this.summaryHelper.publishNoLog(summaryProto, eventType);
    } finally {
      if (scope != null) {
        try {
          scope.close();
        } catch (Throwable ignored) {
        }
      }
      if (span != null) {
        try {
          span.finish();
        } catch (Throwable ignored) {
        }
      }
    }
  }

  public void processStream(final Iterator<CreateGenerativeSummaryResponse> responseIterator,
      final String conversationId, final GenAISummaryCache config, final SummaryDoc summaryDoc,
      final long eventTime) {
    long startTime = DateUtil.TIME.millis();
    int counter = 0;
    final String requestId = UUID.randomUUID().toString();
    final Map<String, Integer> currentIndexMap = new HashMap<>();
    String lastProcessedSectionId = null; // to track the section id processed at the absolute end.
    GenAiSummaryDoc bufferedGenAiSummary =
        null; // to store buffered summary, to be reset after each buffer fill.
    int bufferSize = 2;
    log.debug("responseIterator:{}", responseIterator);

    while (responseIterator.hasNext()) {
      CreateGenerativeSummaryResponse response = responseIterator.next();
      log.info("Beginning processing summary stream for conversation id :{}", conversationId);
      counter++;
      if (response == null) {
        log.error("response from stream is null for stream response number {}", counter);
        continue;
      }

      boolean eofReached = response.getSectionTextList().stream()
          .anyMatch(st -> EOF_IN_ID.equalsIgnoreCase(st.getSection().getId()));
      log.debug("eofReached:{}",eofReached);
      if (eofReached(conversationId, config, summaryDoc, eofReached, bufferedGenAiSummary)) {
        break;
      }

      if (Objects.equals(sectionConvMap.get(conversationId), response.getSectionTextList().get(0).getSection().getId())) {
        numberCounterMap.put(conversationId, numberCounterMap.get(conversationId) + 1);
      } else {
          log.debug("Resetting numeric counter for conversation id: {} as section ids do not match: {} and {}",
            conversationId, sectionConvMap.get(conversationId), response.getSectionTextList().get(0).getSection().getId());
        sectionConvMap.put(conversationId, response.getSectionTextList().get(0).getSection().getId());
        numberCounterMap.put(conversationId, 1);
      }
      log.debug("response number {} from stream : {} requestId:{}", counter, response, requestId);
      this.persistStreamResponse(response, requestId);

      Pair<GenAiSummaryDoc, String> formattedGenAiSummaryPair =
          this.formatSummaryStream(response, config, currentIndexMap, summaryDoc,
              numberCounterMap.get(conversationId), requestId);
      GenAiSummaryDoc formattedGenAiSummary = formattedGenAiSummaryPair.getLeft();
      lastProcessedSectionId = formattedGenAiSummaryPair.getRight();

      log.debug("formatted summary token for conversation id: {} is: {}", conversationId,
          formattedGenAiSummary.getSections());

      // buffer the summary. if the buffer is full, send the summary.
      Pair<GenAiSummaryDoc, Boolean> bufferedSummaryPair =
          this.bufferSummary(bufferedGenAiSummary, bufferSize, formattedGenAiSummary);
      bufferedGenAiSummary = bufferedSummaryPair.getLeft();
      log.debug("buffered summary token for conversation id: {} is: {}", conversationId,
          bufferedGenAiSummary.getSections());

      if (bufferedSummaryPair.getRight() || !responseIterator.hasNext()) {
        // summary can be sent now, because the buffer is full.
        // OR
        // There are no more responses to process.
        log.debug("tempt FF value: {},temptId isPresent: {}",
            this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
                FF_CONTEXT_KIND), StringUtils.isNotBlank(summaryDoc.getTemplateId()));

        if (!this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
            FF_CONTEXT_KIND) || StringUtils.isBlank(summaryDoc.getTemplateId())) {
          this.addMissingSections(bufferedGenAiSummary, config.isFormatEnabled(), config);
        }
        log.debug("final buffered summary token for conversation id: {} is: {}", conversationId,
            bufferedGenAiSummary.getSections());
        summaryDoc.setGenAiSummary(bufferedGenAiSummary);
        summaryDoc.setGenAiGeneratedSummary(bufferedGenAiSummary);
        this.publishAsNewTraceRoot(
            ProtoConverter.getSummaryProto(summaryDoc, Collections.emptyList()), Constants.GEN_AI,
            conversationId);

        log.debug("pushed buffered stream summary for conversation id: {}, summary: {}",
            summaryDoc.getConversationId(), bufferedGenAiSummary.getSections());

        // reset the buffer
        bufferedGenAiSummary = null;
        // increase the buffer size.
        bufferSize = this.increaseBufferSize(bufferSize);
        log.trace("conversation id: {}, buffer size: {}", summaryDoc.getConversationId(),
            bufferSize);
      }
      long latency = DateUtil.TIME.millis() - startTime;
      this.metricHelper.pushGenAILatencyMetricForSummaryGenerate(
          GEN_AI_SUMMARY_LLM_INFERENCE_API_LATENCY_UASSIST_SUMMARY, conversationId,
          summaryDoc.getExperienceId(), latency, MetricScope.METRIC_SCOPE_GEN_AI_SUMMARY);
    }

    // further processing only required if any processing actually done.
    if (null != this.genAIConvSectionMap.get(requestId)) {

      // send final control message to indicate end of stream
      this.publishAsNewTraceRoot(ProtoConverter.getSummaryProto(
          this.controlMessage(summaryDoc, lastProcessedSectionId, config.isFormatEnabled(), config,
              presentationFormatMap.get(conversationId)),
          Collections.emptyList()), Constants.GEN_AI, conversationId);
      log.debug("pushed control message for conversation id: {}, summary: {}",
          summaryDoc.getConversationId(), summaryDoc.getGenAiSummary().getSections());
      CreateGenerativeSummaryResponse finalResponse =
          this.createFinalResponse(this.genAIConvSectionMap.get(requestId));
      log.debug("final completed summary response: {} ", finalResponse);
      GenAiSummaryDoc finalGenAiSummary = null;
      if ((this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
          FF_CONTEXT_KIND) && StringUtils.isNotBlank(summaryDoc.getTemplateId()))) {
        finalGenAiSummary =
            this.formatTemplateBasedSummaryStream(conversationId, Optional.of(finalResponse),
                config, summaryDoc);
      } else {
        finalGenAiSummary =
            this.formattedSummaryDoc(conversationId, Optional.of(finalResponse), config,
                summaryDoc);
      }
      log.debug("final completed genAiSummary after formatting: {} ", finalGenAiSummary);
      summaryDoc.setGenAiSummary(finalGenAiSummary);
      summaryDoc.setGenAiGeneratedSummary(finalGenAiSummary);

      log.info("Gen AI summary stream completed for conversationId: {}", conversationId);

      this.presentationFormatMap.remove(conversationId);
      this.genAIConvSectionMap.remove(requestId);
      this.sectionConvMap.remove(conversationId);
      this.numberCounterMap.remove(conversationId);
    }
  }

  public GenAiSummaryDoc processStreamWithoutUassistConfig(final Iterator<CreateGenerativeSummaryResponse> responseIterator,
      final String conversationId) {
    GenAiSummaryDoc bufferedGenAiSummary = GenAiSummaryDoc.builder().sections(new ArrayList<>()).build(); // to store buffered summary, to be reset after each buffer fill.
    log.debug("processStreamWithoutUassistConfig:: responseIterator:{}", responseIterator);
    Map<String,FormatState> sectionIdCountMap=new HashMap<>();


    while (responseIterator.hasNext()) {
      CreateGenerativeSummaryResponse response = responseIterator.next();
      log.info("processStreamWithoutUassistConfig:: summary itereator conversationId {} response {}",conversationId,response);
      if (response == null) {
        log.error("processStreamWithoutUassistConfig:: response from stream is null for stream response conversationId {}", conversationId);
        continue;
      }
      PresentationFormat format=response.getPresentationFormat();
      boolean eofReached = response.getSectionTextList().stream()
          .anyMatch(st -> EOF_IN_ID.equalsIgnoreCase(st.getSection().getId()));
      log.debug("processStreamWithoutUassistConfig:: eofReached:{}",eofReached);

      //handle
      this.cumulateSummaryResponse(response,bufferedGenAiSummary,format,sectionIdCountMap,conversationId);
      //if eof reached end the stream
      if (eofReached) {
        break;
      }
    }
    return bufferedGenAiSummary;
  }

  private void cumulateSummaryResponse(CreateGenerativeSummaryResponse response,final GenAiSummaryDoc bufferedGenAiSummary,final PresentationFormat format,final Map<String, FormatState> sectionIdCountMap,final String conversationId) {
    response.getSectionTextList().forEach(s->{
      formatTemplateBasedSummaryForSection(s.getSection(),s.getText(),bufferedGenAiSummary,format,sectionIdCountMap,conversationId);
    });
  }

  private void formatTemplateBasedSummaryForSection(Section section,String newText,GenAiSummaryDoc bufferedGenAiSummary, PresentationFormat format, Map<String, FormatState> sectionIdCountMap,
      String conversationId) {
    log.debug("Stream formatting with response-based template, formatType: {} conversationId {}", format,conversationId);
    GenAiSummaryDoc.Section sectionDoc=null;
    int numberIncrement=0;
    if(section.getId().equalsIgnoreCase(EOF_IN_ID)){
      return;
    }
    log.info("formatTemplateBasedSummaryForSection:: conversation Id: {} buffered summary doc: {} , current section {}, new summary text: {}, format: {},section state map: {}",conversationId,bufferedGenAiSummary,sectionDoc,newText,format,sectionIdCountMap);
    if(bufferedGenAiSummary.getSections().stream().noneMatch(e->e.getId().equalsIgnoreCase(section.getId()))) {
      sectionDoc =
          GenAiSummaryDoc.Section.builder().id(section.getId()).text(EMPTY).build();
      bufferedGenAiSummary.getSections().add(sectionDoc);
    } else {
      sectionDoc=bufferedGenAiSummary.getSections().stream()
          .filter(e->e.getId().equalsIgnoreCase(section.getId())).findFirst().orElse(null);
    }
    if(sectionDoc!=null) {
      log.info("processStreamWithoutUassistConfig:: before update section doc: {}",sectionDoc);
      String existingText = sectionDoc.getText();
      List<String> splitNewWords = List.of(newText.split(GENAI_SUMMARY_FORMAT_PATTERN));
      log.info("processStreamWithoutUassistConfig:: split new words: {}",splitNewWords);
      switch (format) {
        case PRESENTATION_FORMAT_BULLET:
          StringBuilder bulletBuilder = new StringBuilder();
          for (String word : splitNewWords) {
            if (word.contains(DOT)) {
              bulletBuilder.append(word).append(NEWLINE_BULLET);
            } else {
              bulletBuilder.append(EMPTY_SPACE).append(word);
            }
          }
          if (existingText.isEmpty()) {
            existingText = NEWLINE_BULLET+bulletBuilder;
          } else {
            existingText = existingText + EMPTY_SPACE + bulletBuilder;
          }
          sectionDoc.setText(existingText);
          break;
        case PRESENTATION_FORMAT_NUMERIC:
          if (!sectionIdCountMap.containsKey(section.getId())
              || sectionIdCountMap.get(section.getId()).getCount() == 1) {
            numberIncrement++;
          } else {
            numberIncrement = sectionIdCountMap.get(section.getId()).getNumberCount();
          }
          StringBuilder numericBuilder = new StringBuilder();
          for (String word : splitNewWords) {
            numericBuilder.append(numberIncrement).append(DOT).append(EMPTY_SPACE).append(word);
            if(word.contains(DOT)){
              numericBuilder.append(EMPTY_SPACE);
            }
            numberIncrement++;
          }
          if (existingText.isEmpty()) {
            existingText = numericBuilder.toString();
          } else {
            existingText = existingText + EMPTY_SPACE + numericBuilder;
          }
          sectionDoc.setText(existingText);
          break;
        case PRESENTATION_FORMAT_PARAGRAPH:
          StringBuilder paragraphBuilder = getParagraphBuilder(splitNewWords);
            log.info("processStreamWithoutUassistConfig:: existing text: {},splittext: {} paragraphBuilder: {}",existingText,splitNewWords,paragraphBuilder);
          if (existingText.isEmpty()) {
            existingText = paragraphBuilder.toString();
          } else {
            existingText = existingText + EMPTY_SPACE + paragraphBuilder;
          }
          log.info("processStreamWithoutUassistConfig:: updated existing text: {}",existingText);
          sectionDoc.setText(existingText);
          break;
        default:
          for (String word : splitNewWords) {
            if (existingText.isEmpty()) {
              existingText = word;
            } else {
              existingText = existingText + EMPTY_SPACE + word;
            }
            sectionDoc.setText(existingText);
          }
          break;
      }
      log.info("processStreamWithoutUassistConfig:: updated section doc: {}",sectionDoc);
      final int n=numberIncrement;
      sectionIdCountMap.compute(section.getId(), (k, v) -> {
        if (null == v) {
          v = FormatState.builder().count(n).numberCount(n).build();
        } else {
          v.setCount(n);
          v.setNumberCount(n);
        } return v;
      });
    }
  }

  private static @NotNull StringBuilder getParagraphBuilder(List<String> newText) {
    StringBuilder paragraphBuilder = new StringBuilder();

    for (String part : newText) {
      if (part == null || part.isEmpty())
        continue;

      String cleaned = part.replace(NEW_LINE, EMPTY);

      if (!paragraphBuilder.isEmpty()) {
        paragraphBuilder.append(EMPTY_SPACE);   // Add space between words
      }
      paragraphBuilder.append(cleaned);      // Append the cleaned text
    }

    log.debug("Paragraph text: {}", paragraphBuilder.toString());
    return paragraphBuilder;
  }

  private boolean eofReached(String conversationId, GenAISummaryCache config, SummaryDoc summaryDoc,
      boolean eofReached, GenAiSummaryDoc bufferedGenAiSummary) {
    if (eofReached) {
      log.info(
          "EOF section reached for conversationId={}, flushing buffer and stopping processing.",
          conversationId);
      if (bufferedGenAiSummary != null && !bufferedGenAiSummary.getSections().isEmpty()) {
        log.debug(
            "Flushing buffered summary (wordSize < bufferSize) due to EOF, conversationId: {}",
            conversationId);
        if (!this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
            FF_CONTEXT_KIND) || StringUtils.isBlank(summaryDoc.getTemplateId())) {
          this.addMissingSections(bufferedGenAiSummary, config.isFormatEnabled(), config);
        }
        summaryDoc.setGenAiSummary(bufferedGenAiSummary);
        summaryDoc.setGenAiGeneratedSummary(bufferedGenAiSummary);
        this.publishAsNewTraceRoot(
            ProtoConverter.getSummaryProto(summaryDoc, Collections.emptyList()), Constants.GEN_AI,
            conversationId);
        log.debug("Published final buffered summary for conversationId: {}, summary: {}",
            summaryDoc.getConversationId(), bufferedGenAiSummary.getSections());
      }
      return true;
    }
    return false;
  }

  private int increaseBufferSize(int bufferSize) {
    bufferSize += bufferSize / 2;
    if (bufferSize > MAX_BUFFER_SIZE) {
      bufferSize = MAX_BUFFER_SIZE;
    }
    return bufferSize;
  }

  Pair<GenAiSummaryDoc, Boolean> bufferSummary(GenAiSummaryDoc bufferedGenAiSummary, int bufferSize,
      GenAiSummaryDoc formattedGenAiSummary) {
    if (null == bufferedGenAiSummary) {
      log.debug("Buffer is null, new buffer initialized...");

      bufferedGenAiSummary =
          GenAiSummaryDoc.builder().sections(new ArrayList<>(formattedGenAiSummary.getSections()))
              .build();
    } else {
      log.debug("buffer exists, adding section wise data...");
      GenAiSummaryDoc finalBufferedGenAiSummary = bufferedGenAiSummary;
      formattedGenAiSummary.getSections().forEach(s -> {
        String id = s.getId();
        if (finalBufferedGenAiSummary.getSections().stream()
            .noneMatch(section -> section.getId().equals(id))) {
          finalBufferedGenAiSummary.getSections().add(s);
        } else {
          finalBufferedGenAiSummary.getSections().stream()
              .filter(section -> section.getId().equals(id)).findFirst().ifPresent(section -> {
                String text = section.getText();
                String newText = s.getText();
                if (StringUtils.isNotEmpty(newText)) {
                  section.setText(String.format(STR_STR, text, newText));
                }
              });
        }
      });
    }

    log.debug("current buffer summary: {}", bufferedGenAiSummary.getSections());

    int wordsize = 0;
    for (GenAiSummaryDoc.Section section : bufferedGenAiSummary.getSections()) {
      wordsize += section.getText().trim().split("\\s+").length;
      log.debug("word size:{}", wordsize);
    }

    if (wordsize > bufferSize) {
      log.debug("Buffer size reached, sending summary");
      return Pair.of(bufferedGenAiSummary, true);
    } else {
      log.debug("Buffer size not reached, current word size: {} and buffer size: {}", wordsize,
          bufferSize);
      return Pair.of(bufferedGenAiSummary, false);
    }
  }

  SummaryDoc controlMessage(SummaryDoc summaryDoc, final String lastSectionId,
      final boolean isFormatEnabled, GenAISummaryCache config, PresentationFormat presentationFormat) {
    log.debug("Creating control message for conversationId: {}", summaryDoc.getConversationId());
    if (null == summaryDoc.getGenAiSummary()) {
      summaryDoc.setGenAiSummary(GenAiSummaryDoc.builder().build());
    }
    List<GenAiSummaryDoc.Section> sections = new ArrayList<>();
    if (presentationFormat == PresentationFormat.PRESENTATION_FORMAT_UNFORMATTED) {
      sections.add(GenAiSummaryDoc.Section.builder().id(Constants.SUMMARY).text(EOF).build());
    } else {
      sections.add(GenAiSummaryDoc.Section.builder().id(lastSectionId).text(EOF).build());
    }
    summaryDoc.getGenAiSummary().setSections(sections);
    if (!this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY,
        FF_CONTEXT_KIND) || StringUtils.isBlank(summaryDoc.getTemplateId())) {
      this.addMissingSections(summaryDoc.getGenAiSummary(), config.isFormatEnabled(), config);
    }
    return summaryDoc;
  }

  void persistStreamResponse(CreateGenerativeSummaryResponse response, String requestId) {
    log.debug("Persisting stream response for conversationId: {}", requestId);
    Map<String, String> sections =
        this.genAIConvSectionMap.computeIfAbsent(requestId, k -> new LinkedHashMap<>());
    response.getSectionTextList().forEach(sectionText -> {
      String sectionId = sectionText.getSection().getId();
      String text = sectionText.getText();
      if (sections.containsKey(sectionId)) {
        String newStr = String.format(STR_STR, sections.get(sectionId), text);
        sections.put(sectionId, newStr);
      } else {
        sections.put(sectionId, text);
      }
    });
    log.debug("persisted response :{}, sectionMap:{}", response, sections);
  }

  private void addMissingSections(GenAiSummaryDoc formattedGenAiSummary, boolean isFormatEnabled,
      GenAISummaryCache config) {
    log.debug("Adding missing sections to genAI summary: {}", formattedGenAiSummary.getSections());
    List<Boolean> conditions = new ArrayList<>();
    if (Objects.nonNull(config.getSummaryOptIn())) {
      conditions = Arrays.asList(config.getSummaryOptIn().isCallReasonEnabled(),
          config.getSummaryOptIn().isAgentStepsEnabled(),
          config.getSummaryOptIn().isOutcomeEnabled(),
          config.getSummaryOptIn().isCustomerSatisfactionEnabled());
    }
    if (isFormatEnabled) {
      boolean allFalse = conditions.stream().noneMatch(condition -> condition);
      if (!allFalse) {
        for (String section : SECTIONS) {
          if (config.getSummaryOptIn().isCallReasonEnabled()) {
            log.debug("Adding call reason missing section");
            checkAndAddEmptySection(formattedGenAiSummary, CALL_REASON);
          }
          if (config.getSummaryOptIn().isAgentStepsEnabled()) {
            log.debug("Adding agent steps missing section");
            checkAndAddEmptySection(formattedGenAiSummary, AGENT_ACTIONS);
          }
          if (config.getSummaryOptIn().isOutcomeEnabled()) {
            log.debug("Adding outcome missing section");
            checkAndAddEmptySection(formattedGenAiSummary, CALL_OUTCOME);
          }
          if (config.getSummaryOptIn().isCustomerSatisfactionEnabled()) {
            log.debug("Adding customer satisfaction missing section");
            checkAndAddEmptySection(formattedGenAiSummary, CUSTOMER_SENTIMENT);
          }
        }
      } else {
        for (String section : SECTIONS) {
          checkAndAddEmptySection(formattedGenAiSummary, section);
        }
      }
      log.debug("After adding missing sections: {}", formattedGenAiSummary.getSections());
    } else {
      // summary
      this.checkAndAddEmptySection(formattedGenAiSummary, Constants.SUMMARY);
      this.checkAndAddEmptySection(formattedGenAiSummary, Constants.CUSTOMER_SENTIMENT);
    }

  }

  private void checkAndAddEmptySection(GenAiSummaryDoc formattedGenAiSummary, String section) {
    if (formattedGenAiSummary.getSections().stream().noneMatch(s -> s.getId().equals(section))) {
      formattedGenAiSummary.getSections()
          .add(GenAiSummaryDoc.Section.builder().id(section).text(EMPTY).build());
    }
  }

  CreateGenerativeSummaryResponse createFinalResponse(Map<String, String> sectionMap) {
    List<SectionText> sections = sectionMap.entrySet().stream().map(
        entry -> SectionText.newBuilder().setSection(Section.newBuilder().setId(entry.getKey()))
            .setText(entry.getValue()).build()).collect(Collectors.toList());
    return CreateGenerativeSummaryResponse.newBuilder().addAllSectionText(sections).build();
  }

  Map<String, Map<String, String>> getGenAiConversationSectionMap() {
    return this.genAIConvSectionMap;
  }

  Pair<GenAiSummaryDoc, String> formatSummaryStream(CreateGenerativeSummaryResponse summaryResponse,
      GenAISummaryCache config, Map<String, Integer> currentNumberIndexMap, SummaryDoc summaryDoc,
      Integer isFirstWord) {
    return this.formatSummaryStream(summaryResponse, config, currentNumberIndexMap, summaryDoc,
        isFirstWord, null);
  }

  Pair<GenAiSummaryDoc, String> formatSummaryStream(CreateGenerativeSummaryResponse summaryResponse,
      GenAISummaryCache config, Map<String, Integer> currentNumberIndexMap, SummaryDoc summaryDoc,
      Integer isFirstWord, String requestId) {
    final GenAiSummaryDoc genAiSummary =
        GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    Stream<SectionText> sectionTextStream = summaryResponse.getSectionTextList().stream();

    if (summaryResponse.getSectionTextCount() > 1 && !this.ldHelper.isFFEnabled(
        Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)) {
      sectionTextStream = sectionTextStream.sorted(
          Comparator.comparingInt(section -> SECTIONS.indexOf(section.getSection().getId())));
    }

    AtomicReference<String> currentSectionIdForUnformatted = new AtomicReference<>();
    AtomicReference<String> currentSectionId = new AtomicReference<>();

    boolean formatted = config.isFormatEnabled();

    if (this.ldHelper.isFFEnabled(Constants.ENABLE_NG_TEMPLATE_BASED_GENAI_SUMMARY, FF_CONTEXT_KIND)
        && ObjectUtils.isNotEmpty(summaryResponse.getPresentationFormat())
        && StringUtils.isNotBlank(summaryDoc.getTemplateId())) {

      return this.formatWithTemplateBased(summaryDoc, genAiSummary, sectionTextStream,
          summaryResponse.getPresentationFormat(), isFirstWord, currentSectionId, requestId);
    }

    sectionTextStream.forEach(sectionText -> {
      if (formatted) {
        this.handleFormattedStream(config, currentNumberIndexMap, sectionText, genAiSummary);
        currentSectionId.set(sectionText.getSection().getId());
      } else {
        currentSectionIdForUnformatted.set(this.handleUnformattedStream(sectionText, genAiSummary,
            currentSectionIdForUnformatted.get()));
        currentSectionId.set(sectionText.getSection().getId().equals(CUSTOMER_SENTIMENT)
            ? CUSTOMER_SENTIMENT
            : SUMMARY);
      }
    });

    return Pair.of(genAiSummary, currentSectionId.get());
  }

  private GenAiSummaryDoc formatTemplateBasedSummaryStream(String conversationId,
      Optional<CreateGenerativeSummaryResponse> getAISummaryFromModelOpt, GenAISummaryCache config,
      SummaryDoc summaryDoc) {
    final GenAiSummaryDoc genAiSummary =
        GenAiSummaryDoc.builder().sections(new ArrayList<>()).build();
    Stream<SectionText> sectionTextStream =
        getAISummaryFromModelOpt.get().getSectionTextList().stream();
    PresentationFormat format = this.presentationFormatMap.get(conversationId);

    log.debug("Stream formatting summary-response: {}", getAISummaryFromModelOpt.get());

    // This method internally populates genAiSummary and returns it
    this.formatWithTemplateBased(summaryDoc, genAiSummary, sectionTextStream, format, null, null,
        null);

    return genAiSummary;
  }

  private Pair<GenAiSummaryDoc, String> formatWithTemplateBased(SummaryDoc summaryDoc,
      GenAiSummaryDoc genAiSummary, Stream<SectionText> sectionTextStream,
      PresentationFormat format, Integer isFirstWord, AtomicReference<String> currentSectionId,
      String requestId) {
    log.debug("Stream formatting with response-based template, formatType: {}", format);
    if (format == PresentationFormat.PRESENTATION_FORMAT_BULLET
        || format == PresentationFormat.PRESENTATION_FORMAT_NUMERIC
        || format == PresentationFormat.PRESENTATION_FORMAT_PARAGRAPH) {
      sectionTextStream.forEach(sectionText -> {
        GenAiSummaryDoc.Section sectionDoc = GenAiSummaryDoc.Section.builder().build();
        Section section = sectionText.getSection();
        sectionDoc.setId(section.getId());
        log.debug("Processing section id: {} and sectionText: {}", section.getId(), sectionText.getText());
        String rawText = sectionText.getText();
        String finalText;
        if (StringUtils.isBlank(rawText)) {
          // A paragraph break arrives as its own blank chunk. Turning it into ". " invents a full
          // stop the model never produced, which renders as a line containing just ".". Collapse it
          // to a space instead — the chunk after a break can arrive with no leading space, so the
          // separator still has to be there.
          finalText = rawText.replace(DOUBLE_NEW_LINE, EMPTY_SPACE).replace(NEW_LINE, EMPTY_SPACE);
        } else if (rawText.contains(DOUBLE_NEW_LINE) && rawText.trim().endsWith(DOT)) {
          finalText = rawText.replace(DOUBLE_NEW_LINE, EMPTY);
        } else {
          finalText = rawText.replace(DOUBLE_NEW_LINE, DOT_SINGLE_SPACE);
        }

        log.debug("Replaced text for section id: {} is: {}", section.getId(), finalText);
        String textSoFar = this.sectionTextSoFar(requestId, section.getId());
        String formattedText = (isFirstWord != null)
            ? this.formatTextOnFormatTypeTemplateBased(GenAiSummaryHelper.getFormatType(format),
                finalText, isFirstWord, textSoFar)
            : this.formatTextBasedOnFormatType(GenAiSummaryHelper.getFormatType(format),
                finalText);

        sectionDoc.setText(formattedText);
        genAiSummary.getSections().add(sectionDoc);
        if (currentSectionId != null) {
          currentSectionId.set(section.getId());
        }
      });

      this.presentationFormatMap.putIfAbsent(summaryDoc.getConversationId(), format);
      return Pair.of(genAiSummary, currentSectionId != null ? currentSectionId.get() : null);

    } else if (format == PresentationFormat.PRESENTATION_FORMAT_UNFORMATTED) {
      log.debug("Streaming for unformatted summary");
      GenAiSummaryDoc.Section sectionDocUnformatted =
          GenAiSummaryDoc.Section.builder().id(Constants.SUMMARY).text(StringUtils.EMPTY).build();

      sectionTextStream.forEach(s -> {
        String text = getUnformattedTextStream(s, sectionDocUnformatted, s.getSection());
        sectionDocUnformatted.setText(text);
        if (currentSectionId != null) {
          currentSectionId.set(s.getSection().getId());
        }
      });

      genAiSummary.getSections().add(sectionDocUnformatted);

      log.debug("Unformatted GenAISummary: {}, currentSectionId: {}", genAiSummary,
          currentSectionId != null ? currentSectionId.get() : null);

      this.presentationFormatMap.putIfAbsent(summaryDoc.getConversationId(), format);
      return Pair.of(genAiSummary, currentSectionId != null ? currentSectionId.get() : null);
    }

    return Pair.of(genAiSummary, null);
  }

  private String handleUnformattedStream(final SectionText sectionText,
      final GenAiSummaryDoc genAiSummary, String currentSectionId) {
    final String sectionId = sectionText.getSection().getId().equals(CUSTOMER_SENTIMENT)
        ? Constants.CUSTOMER_SENTIMENT
        : Constants.SUMMARY;
    String text = sectionText.getText();
    // for any section which is not customer sentiment, add the text to the map for later processing.
    if (!sectionId.equals(CUSTOMER_SENTIMENT)) {
      GenAiSummaryDoc.Section existingSection = this.getExistingSection(genAiSummary, sectionId);
      // attempting to switch section on the basis of what was the last section worked upon being same as current section.
      String finalText =
          sectionText.getSection().getId().equals(currentSectionId)
              ? String.format(STR_TAG_N_STR, existingSection.getText(), text)
              : String.format(STR_STR, existingSection.getText(), text);

      existingSection.setText(finalText);
      currentSectionId = sectionText.getSection().getId();
    } else {
      // customer sentiment can directly be added in without any processing.
      genAiSummary.getSections()
          .add(GenAiSummaryDoc.Section.builder().id(CUSTOMER_SENTIMENT).text(text).build());
    }
    return currentSectionId;
  }

  // fetches the existing entry for the sectionId, if not found, creates a new entry.
  private GenAiSummaryDoc.Section getExistingSection(GenAiSummaryDoc genAiSummary,
      String sectionId) {
    return genAiSummary.getSections().stream().filter(section -> section.getId().equals(sectionId))
        .findFirst().orElseGet(() -> {
          GenAiSummaryDoc.Section newSection =
              GenAiSummaryDoc.Section.builder().id(sectionId).text(Strings.EMPTY).build();
          genAiSummary.getSections().add(newSection);
          return newSection;
        });
  }

  void handleFormattedStream(final GenAISummaryCache config,
      final Map<String, Integer> currentNumberIndex, SectionText sectionText,
      GenAiSummaryDoc genAiSummary) {
    final String sectionId = sectionText.getSection().getId();
    String text = sectionText.getText();
    String formatString = this.resolveFormatString(config, sectionId);
    String tempText = null;
    switch (formatString) {
      case BULLET_POINT:
        if (null == currentNumberIndex.get(sectionId)) {
          // initialize the beginning of the section
          currentNumberIndex.put(sectionId, 1);
          tempText = START_OF_BULLET_REPLACEMENT;
        }
        // break lines into bullet points on the basis of sentence end.
        if (text.contains(DOT)) {
          // considering the case where a token may contain multiple instances of new line (section wise?)
          text = text.replaceAll(REGEX_DOT, STREAM_BULLET_REPLACEMENT);
        }
        if (null != tempText) {
          text = String.format(STR_STR, tempText, text);
        }
        break;
      case NUMBER_LIST:
        // break lines into number list on the basis of sentence end.
        if (null == currentNumberIndex.get(sectionId)) {
          // initialize the beginning of the section
          currentNumberIndex.put(sectionId, 1);
          tempText = String.format(TAG_N_NUM_DOT, currentNumberIndex.get(sectionId));
        }
        if (text.contains(DOT)) {
          Matcher matcher = Pattern.compile(REGEX_DOT).matcher(text);
          StringBuilder updatedText = new StringBuilder();
          int finalIndex = currentNumberIndex.get(sectionId);

          while (matcher.find()) {
            finalIndex++;
            matcher.appendReplacement(updatedText,
                String.format(STREAM_NUMBER_LIST_REPLACEMENT, finalIndex));
          }
          matcher.appendTail(updatedText);

          text = updatedText.toString();
          currentNumberIndex.put(sectionId, finalIndex);
        }
        if (null != tempText) {
          text = String.format(STR_STR, tempText, text);
        }
        break;
      case PARAGRAPH:
      default:
        // no need to do anything, just add the text as is.
        log.debug("No formatting needed for sectionId: {}", sectionId);
        break;
    }
    genAiSummary.getSections()
        .add(GenAiSummaryDoc.Section.builder().id(sectionId).text(text).build());
  }

  String formatDotSpaceToTagN(String text) {
    boolean dotFlag = false;
    StringBuilder finalStringBuilder = new StringBuilder();
    for (Character c : text.toCharArray()) {
      if (c == '.' || c == '\u002E') {
        dotFlag = true;
      } else if (!Character.isDigit(c) && dotFlag) {
        // the character after the point is NOT a digit.
        log.info("character {} is a non digit after dot", c);
        finalStringBuilder.append(TAG_N);
        dotFlag = false;
      } else {
        dotFlag = false;
      }
      finalStringBuilder.append(c);
    }
    String finalString = finalStringBuilder.toString();
    log.info("final String for an unformatted stream is {}", finalString);
    return finalString;
  }
}

@Builder
@Getter
@Setter
class FormatState{
  private int count;
  private int numberCount;
}
