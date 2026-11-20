package com.uniphore.uassist.summary.dao.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Document("HeaderTemplate")
@Data
@Builder(toBuilder = true)
public class HeaderTemplateDoc {

  @Id
  private String id;
  private String fieldId;
  @Indexed
  private String businessProcessId;
  private String template;
  private String tenantId;
  private String environment;
  @Default
  private boolean deleted = Boolean.FALSE;
  private String userId;
  private String createdTimestamp;
  private String modifiedTimestamp;

}
