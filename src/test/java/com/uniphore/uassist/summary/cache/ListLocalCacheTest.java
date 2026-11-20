package com.uniphore.uassist.summary.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ListLocalCacheTest {

  private ListLocalCache<String> listLocalCache;
  private Cache<String, List<String>> mockCache;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
    mockCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    listLocalCache = new ListLocalCache<>(10, TimeUnit.MINUTES);
  }

  @Test
  void testAdd() {
    listLocalCache.add("key1", "value1");
    List<String> result = listLocalCache.get("key1");
    assertNotNull(result);
    assertTrue(result.contains("value1"));
  }

  @Test
  void testAddAll() {
    listLocalCache.addAll("key2", List.of("value1", "value2"));
    List<String> result = listLocalCache.get("key2");
    assertNotNull(result);
    assertTrue(result.contains("value1"));
    assertTrue(result.contains("value2"));
  }

  @Test
  void testPush() {
    listLocalCache.push("key3", "value1");
    listLocalCache.push("key3", "value2");
    List<String> result = listLocalCache.get("key3");
    assertNotNull(result);
    assertTrue(result.contains("value1"));
    assertTrue(result.contains("value2"));
  }

  @Test
  void testPushAll() {
    listLocalCache.pushAll("key4", List.of("value1", "value2"));
    List<String> result = listLocalCache.get("key4");
    assertNotNull(result);
    assertTrue(result.contains("value1"));
    assertTrue(result.contains("value2"));
  }

  @Test
  void testPop() {
    listLocalCache.add("key5", "value1");
    listLocalCache.add("key5", "value2");
    String result = listLocalCache.pop("key5");
    assertEquals("value2", result);
    List<String> list = listLocalCache.get("key5");
    assertNotNull(list);
    assertTrue(list.contains("value1"));
    assertFalse(list.contains("value2"));
  }

  @Test
  void testSize() {
    listLocalCache.add("key6", "value1");
    listLocalCache.add("key6", "value2");
    int size = listLocalCache.size("key6");
    assertEquals(2, size);
  }

  @Test
  void testDelete() {
    listLocalCache.add("key7", "value1");
    listLocalCache.delete("key7");
    List<String> result = listLocalCache.get("key7");
    assertNull(result);
  }

  @Test
  void testDeleteAll() {
    listLocalCache.add("session1_key1", "value1");
    listLocalCache.add("session1_key2", "value2");
    listLocalCache.add("session2_key1", "value3");

    listLocalCache.deleteAll("session1");

    List<String> result1 = listLocalCache.get("session1_key1");
    List<String> result2 = listLocalCache.get("session1_key2");
    List<String> result3 = listLocalCache.get("session2_key1");

    assertNull(result1);
    assertNull(result2);
    assertNotNull(result3);
    assertTrue(result3.contains("value3"));
  }
}
