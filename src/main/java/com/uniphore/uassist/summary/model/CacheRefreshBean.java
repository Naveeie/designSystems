package com.uniphore.uassist.summary.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Saroj Kumar
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
public class CacheRefreshBean {

  private String businessProcessId;
  private String eventType;
  private long startTime;
  private String tenantId;
  private String envType;
  private String intentId;

}
