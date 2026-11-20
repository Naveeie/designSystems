package com.uniphore.uassist.summary.dao.documents;

import java.io.Serializable;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;

@Document("GenAISummaryConfig")
@lombok.Data
@Builder(toBuilder = true)
public class GenAISummaryConfigDoc implements Serializable {

  private static final long serialVersionUID = 1L;
  @Id
  private String id;
  private String experienceId;
  private String config;
  private String tenantId;
  private String environment;
  private String userId;
  private String createdTimestamp;
  private String modifiedTimestamp;
}
