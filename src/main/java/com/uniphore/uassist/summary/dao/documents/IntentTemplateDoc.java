package com.uniphore.uassist.summary.dao.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Builder;
import lombok.Builder.Default;

@Document("IntentTemplate")
@lombok.Data
@Builder(toBuilder = true)
public class IntentTemplateDoc {
  @Id
  private String id;
  private String fieldId;
  @Indexed
  private String intentId;
  private String template;
  private String businessProcessId;
  private String experienceId;
  private String environment;
  private String tenantId;
  @Default
  private boolean deleted = Boolean.FALSE;
  private String userId;
  private String createdTimestamp;
  private String modifiedTimestamp;
}
