package com.uniphore.uassist.summary.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.uniphore.featureflag.LaunchDarklyFeatureFlags;
import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.core.tenant.TenantContext;
import com.uniphore.ri.auth.constant.Constant;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PlatformServiceImpl implements PlatformService {

  private static final String SERVICE_UNAVAILABLE_OR_CATEGORY_NOT_FOUND =
      "Service unavailable or category not found";
  
  private static final String CAESIUM_ENABLED_FF = "uassist-caesium-enabled-ff";

  @Value("${ocms.endpoint}")
  private String ocmsEndPoint;

  @Value("${caesium.endpoint:http://caesium:8080}")
  private String caesiumEndPoint;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private LaunchDarklyHelper ldHelper;

  @Override
  public Map<String, String> loadCategoryByExperienceId(final String expId) throws Exception {
    final String serverUrl = this.ocmsEndPoint + "/v2/config/experiences/" + expId + "?environment="
        + TenantContext.getMap().get(Constant.META_KEY_ENVIRONMENT);
    final ResponseEntity<Map> response = this.restTemplate.getForEntity(serverUrl, Map.class);
    if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
      return response.getBody();

    }
    throw new Exception(SERVICE_UNAVAILABLE_OR_CATEGORY_NOT_FOUND);
  }

  @Override
  public Map<String, String> loadCategoryByExperienceIdCaesium(final String expId, String authToken)
      throws Exception {
    final String serverUrl = this.caesiumEndPoint + "/v1/experiences/" + expId + "?environment="
        + TenantContext.getMap().get(Constant.META_KEY_ENVIRONMENT);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", authToken);
    HttpEntity<?> entity = new HttpEntity<>(headers);
    ResponseEntity<Map> response =
        this.restTemplate.exchange(serverUrl, HttpMethod.GET, entity, Map.class);
    if (response.getStatusCode() == HttpStatus.OK) {
      Map<String, Object> body = response.getBody();
      if (body != null) {
        Object data = body.get("data");
        if (data instanceof Map) {
          return (Map<String, String>) data;
        }
      }
    }
    throw new Exception(SERVICE_UNAVAILABLE_OR_CATEGORY_NOT_FOUND);
  }

  @Override
  public Map<String, String> getCategoryMap(String experienceId, String authToken)
      throws Exception {
    Map<String, String> categoryMap = null;
    final boolean ffEnabled =
        this.ldHelper.isFFEnabled(CAESIUM_ENABLED_FF, LaunchDarklyFeatureFlags.FF_CONTEXT_KIND);
    if (ffEnabled) {
     categoryMap= this.loadCategoryByExperienceIdCaesium(experienceId, authToken);
    } else {
      categoryMap = this.loadCategoryByExperienceId(experienceId);
    }
    
    log.debug("Category map for experienceId {}: {}", experienceId, categoryMap);
    return categoryMap;
  }
}
