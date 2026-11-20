package com.uniphore.uassist.summary.cache;

import com.uniphore.platform.core.tenant.TenantConstant;
import com.uniphore.platform.core.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HashLocalCacheTest {

  private HashLocalCache<String> cache;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    cache = new HashLocalCache<>(1, TimeUnit.MINUTES);
    Map<String, String> tenantMap = new HashMap<>();
    tenantMap.put(TenantConstant.META_KEY_TENANT_ID, "3e37631c-78ac-4137-8415-72dcd90b19fd");
    tenantMap.put(TenantConstant.META_KEY_ENVIRONMENT, "dev");
    TenantContext.setMap(tenantMap);
  }

  @Test
  void testPutAndGet() {
    String key = "key1";
    String hashKey = "hashkey1";
    String value = "value1";
    cache.put(key, hashKey, value);
    String retrievedValue = cache.get(key, hashKey);
    assertEquals(value, retrievedValue, "The retrieved value should match the inserted value.");
  }

  @Test
  void testPutAll() {
    String key = "key2";
    Map<String, String> values = Map.of("hashkey1", "value1", "hashkey2", "value2");
    cache.putAll(key, values);
    Map<String, String> retrievedValues = cache.getAll(key);
    assertEquals(values, retrievedValues, "The retrieved values should match the inserted values.");
  }

  @Test
  void testHasKey() {
    String key = "key3";
    String hashKey = "hashkey3";
    String value = "value3";
    cache.put(key, hashKey, value);
    assertTrue(cache.hasKey(key, hashKey), "The key should exist.");
  }

  @Test
  void testGetWhenKeyNotPresent() {
    String key = "key4";
    String hashKey = "hashkey4";
    assertNull(cache.get(key, hashKey),
        "The retrieved value should be null when key is not present.");
  }

  @Test
  void testDelete() {
    String key = "key5";
    String hashKey = "hashkey5";
    String value = "value5";
    cache.put(key, hashKey, value);
    cache.delete(key, hashKey);
    assertNull(cache.get(key, hashKey), "The value should be null after deletion.");
  }

  @Test
  void testDeleteAll() {
    String key = "key6";
    Set<String> hashKeys = Set.of("hashkey6a", "hashkey6b");
    hashKeys.forEach(hashKey -> cache.put(key, hashKey, "value"));
    cache.deleteAll(key, hashKeys);
    assertTrue(cache.getAll(key).isEmpty(),
        "The cache should be empty after deleting all hash keys.");
  }

  @Test
  void testNotExpiry() throws InterruptedException {
    String key = "key7";
    String hashKey = "hashkey7";
    String value = "value7";
    cache.put(key, hashKey, value);
    assertNotNull(cache.get(key, hashKey), "The value should not be null within expiry time");
  }
}
