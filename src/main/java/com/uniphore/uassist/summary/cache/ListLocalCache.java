package com.uniphore.uassist.summary.cache;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class ListLocalCache<T> {
  private Cache<String, List<T>> cache;

  public ListLocalCache(int expiryDuration, TimeUnit timeUnit) {
    cache = Caffeine.newBuilder().expireAfterWrite(expiryDuration, timeUnit)
        .build();
  }

  public List<T> get(String key) {
    return cache.getIfPresent(key);
  }

  public void add(String key, T value) {
    this.push(key, value);
  }
  
  public void addAll(String key, List<T> values) {
    this.pushAll(key, values);
  }
  
  public void push(String key, T value) {
    if (key != null && value != null) {
      List<T> list = get(key);
      if (list == null) {
        list = new CopyOnWriteArrayList<T>();
      }
      list.add(value);
      cache.put(key, list);
    }
  }
  
  public void pushAll(String key, List<T> values) {
    if (key != null && values != null) {
      cache.put(key, new CopyOnWriteArrayList<T>(values));
    }
  }

  public T pop(String key) {
    T t = null;
    if (key != null) {
      List<T> list = get(key);
      if (list != null) {
        t = list.remove(list.size() - 1);
        cache.put(key, list);
      }
    }
    return t;
  }
  
  public int size(String key) {
    return cache.getIfPresent(key).size();
  }

  public void delete(String key) {
    if (key != null) {
      cache.invalidate(key);
    }
  }

  public void deleteAll(String sessionId) {
    if (sessionId != null) {
      cache.invalidateAll(cache.asMap().keySet().stream().filter(key -> key.contains(sessionId))
          .collect(Collectors.toSet()));
    }
  }
}
