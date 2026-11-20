package com.uniphore.uassist.summary.service;

import com.uniphore.uassist.summary.model.GenAISummaryConfig;

import java.util.List;
import java.util.Map;

public interface GenAISummaryConfigService {

  public GenAISummaryConfig getGenAISummaryConfig(final String expId);

  public GenAISummaryConfig upsertGenAISummaryConfig(
      final GenAISummaryConfig genAISummaryConfigReq);

  public Boolean getIsOnDemand(final String expId);

  Map<String, Boolean> validateEntityUsedInGenAISummary(final List<String> entityIdsToValidate,
      final String expId);
}
