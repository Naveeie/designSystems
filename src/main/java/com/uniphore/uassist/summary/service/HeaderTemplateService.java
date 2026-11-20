package com.uniphore.uassist.summary.service;

import java.util.List;

import com.uniphore.uassist.summary.model.HeaderTemplate;
import com.uniphore.uassist.summary.model.MessageResponse;

public interface HeaderTemplateService {

	public MessageResponse upsertHeaderTemplate(final HeaderTemplate headerTemplateReq);

	public HeaderTemplate fetchHeaderTemplate(final String businessProcessId);

	public List<HeaderTemplate> fetchHeaderTemplates(final List<String> businessProcessIds);

}
