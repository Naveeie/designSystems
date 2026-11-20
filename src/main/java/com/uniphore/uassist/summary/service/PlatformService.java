package com.uniphore.uassist.summary.service;

import java.util.Map;

public interface PlatformService {

  public Map<String, String> loadCategoryByExperienceId(final String expId) throws Exception;

  public Map<String, String> loadCategoryByExperienceIdCaesium(final String expId, String authToken)
      throws Exception;

  public Map<String, String> getCategoryMap(String experienceId, String authToken)
      throws Exception;
}
