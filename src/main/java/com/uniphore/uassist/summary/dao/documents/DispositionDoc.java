package com.uniphore.uassist.summary.dao.documents;

import java.io.Serializable;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Builder;

@Document("Disposition")
@lombok.Data
@Builder(toBuilder = true)
public class DispositionDoc implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id
  private String id;
  private String fieldId;
  private String businessProcessId;
  private String name;
  private String description;
  private Map<Long, String> values;
  private String tenantId;
  private String environment;
  private String userId;
  private String createdTimestamp;
  private String modifiedTimestamp;
}
