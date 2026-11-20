package com.uniphore.uassist.summary.event.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventMetricsResponse {
  private int size;
  private String conversationLastEventTime;
}
