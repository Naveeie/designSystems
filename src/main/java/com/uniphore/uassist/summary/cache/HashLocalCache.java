package com.uniphore.uassist.summary.cache;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HashLocalCache<T> {
  private Cache<String, Map<String, T>> hashcache;

  public HashLocalCache(int expiryDuration, TimeUnit timeUnit) {
    hashcache = Caffeine.newBuilder().expireAfterWrite(expiryDuration, timeUnit)
        .build();
  }

  public void put(String key, String hashkey, T value) {
    if (key != null && value != null && hashkey != null) {
      Map<String, T> map = hashcache.getIfPresent(key);
      if (map == null) {
        map = new ConcurrentHashMap<String, T>();
      }
      map.put(hashkey, value);
      hashcache.put(key, map);
      log.debug("HashLocalCache-put key : {} ,hashkey: {}, value: {}",key,  hashkey, value);
    }
  }
  
  public void putAll(String key, Map<String , T> values) {
    if (key != null && values != null) {
      hashcache.put(key, new ConcurrentHashMap<String, T>(values));
    }
  }
  
  public Boolean hasKey(String key, String hashkey) {
    Map<String, T> map = hashcache.getIfPresent(key);
    if(map == null) {
      return null;
    }
    return map.containsKey(hashkey);
  }
  
  public T get(String key, String hashkey) {
    Map<String, T> map = hashcache.getIfPresent(key);
    if(map == null) {
      return null;
    }
    T value = map.get(hashkey);
    log.debug("HashLocalCache-get key : {} ,hashkey: {}, value: {}",key,  hashkey, value);
    return value;
  }
  
  public Map<String , T> getAll(String key) {
    return hashcache.getIfPresent(key);
  }

  public void delete(String key, String hashkey) {
    if (key != null && hashkey != null) {
      Optional.ofNullable(hashcache.getIfPresent(key)).map(map -> map.remove(hashkey));
    }
  }

  public void delete(String key) {
    if (key != null) {
      hashcache.invalidate(key);
    }
  }

  public void deleteAll(String key, Set<String> hashKeys) {
    if (key != null && CollectionUtils.isNotEmpty(hashKeys)) {
      Optional.ofNullable(hashcache.getIfPresent(key)).map(map -> map.keySet().removeAll(hashKeys));
    }
  }
  
}
