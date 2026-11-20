package com.uniphore.uassist.summary.service;

import com.uniphore.featureflag.LaunchDarklyHelper;
import com.uniphore.platform.core.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformServiceImplTest {

  @InjectMocks
  private PlatformServiceImpl service;

  @Mock
  private RestTemplate restTemplate;
  @Mock
  private LaunchDarklyHelper ldHelper;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    Field ocmsField = PlatformServiceImpl.class.getDeclaredField("ocmsEndPoint");
    ocmsField.setAccessible(true);
    ocmsField.set(service, "http://ocms");
    Field caesiumField = PlatformServiceImpl.class.getDeclaredField("caesiumEndPoint");
    caesiumField.setAccessible(true);
    caesiumField.set(service, "http://caesium");
    TenantContext.setTenantContext("testTenant", "dev");
  }

  @Test
  void testLoadCategoryByExperienceId_success() throws Exception {
    Map<String, String> expected = Map.of("catFieldName", "bp1");
    ResponseEntity<Map> response = new ResponseEntity<>(expected, HttpStatus.OK);
    when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(response);
    Map<String, String> result = service.loadCategoryByExperienceId("exp1");
    assertEquals(expected, result);
  }

  @Test
  void testLoadCategoryByExperienceId_failure() {
    ResponseEntity<Map> response = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
    when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(response);

    Exception ex = assertThrows(Exception.class, () -> service.loadCategoryByExperienceId("exp1"));
    assertTrue(ex.getMessage().contains("Service unavailable"));
  }

  @Test
  void testLoadCategoryByExperienceIdCaesium_success() throws Exception {
    Map<String, String> data = Map.of("catFieldName", "bp1");
    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(Map.class))).thenReturn(response);
    Map<String, String> result = service.loadCategoryByExperienceIdCaesium("exp1", "token");
    assertEquals(data, result);
  }

  @Test
  void testLoadCategoryByExperienceIdCaesium_failure() {
    Map<String, Object> body = new HashMap<>();
    body.put("data", null);
    ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(Map.class))).thenReturn(response);
    Exception ex = assertThrows(Exception.class,
        () -> service.loadCategoryByExperienceIdCaesium("exp1", "token"));
    assertTrue(ex.getMessage().contains("Service unavailable"));
  }

  @Test
  void testGetCategoryMap_flagEnabled() throws Exception {
    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(true);
    Map<String, String> data = Map.of("catFieldName", "bp1");
    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    ResponseEntity<Map> response = new ResponseEntity<>(body, HttpStatus.OK);
    when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
        eq(Map.class))).thenReturn(response);
    Map<String, String> result = service.getCategoryMap("exp1", "token");
    assertEquals(data, result);
  }

  @Test
  void testGetCategoryMap_flagDisabled() throws Exception {
    when(ldHelper.isFFEnabled(anyString(), anyString())).thenReturn(false);
    Map<String, String> expected = Map.of("catFieldName", "bp1");
    ResponseEntity<Map> response = new ResponseEntity<>(expected, HttpStatus.OK);
    when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(response);
    Map<String, String> result = service.getCategoryMap("exp1", "token");
    assertEquals(expected, result);
  }
}
