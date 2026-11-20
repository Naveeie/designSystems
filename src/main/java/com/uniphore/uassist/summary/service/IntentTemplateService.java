package com.uniphore.uassist.summary.service;

import java.util.List;

import com.uniphore.uassist.summary.model.IntentTemplate;
import com.uniphore.uassist.summary.model.MessageResponse;

public interface IntentTemplateService {
  public MessageResponse upsertIntentTemplate(final IntentTemplate intentTemplateReq);

  public IntentTemplate getIntentTemplate(final String intentId);

  public List<IntentTemplate> getIntentTemplates(final List<String> intentIds);

  public boolean isVariableUsedInSummary(final String variableId);
  
  public List<IntentTemplate> getIntentTemplatesByExpId(final String expId);
  
  public void deleteIntentTemplateByExpId(final String expId);
}
