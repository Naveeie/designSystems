package com.uniphore.uassist.summary.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.uniphore.platform.cache.CacheOperations;
import com.uniphore.uassist.summary.model.AgentStepPojo;
import com.uniphore.uassist.summary.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentStepsCacheHelper {

  @Value("${app.redis.summary-cache.ttl:3600000}")
  private long cachettl;

  @Autowired
  private CacheOperations<Map<String, AgentStepPojo>> agentStepsCache;

  //fixme: add local cache!

  public List<AgentStepPojo> add(final String convId, final List<AgentStepPojo> agentSteps) {
    final long startTime = DateUtil.TIME.millis();
    logSetStart(convId, agentSteps);
    final String cacheKey = CommonCacheHelper.getAgentStepsCacheKey();
    Map<String, AgentStepPojo> cache =
        this.agentStepsCache.get(cacheKey, convId, new TypeReference<Map<String, AgentStepPojo>>() {
        });
    if (null == cache) {
      log.debug("initializing agent step cache with empty map for list call for conv id {}", convId);
      cache = new HashMap<>(); // intializing map for fresh conversation id
    }
    if (!CollectionUtils.isEmpty(agentSteps)) {
      for (AgentStepPojo agentStep : agentSteps) {
        cache.put(agentStep.getId(), agentStep);
      }
      this.agentStepsCache.put(cacheKey, convId, cache, this.cachettl);
    } else {
      log.debug("no agent steps provided, did not add any new ones for conv id {}", convId);
    }
    logSetEnd(convId, agentSteps, startTime);
    return new ArrayList<>(cache.values());
  }

  public List<AgentStepPojo> add(final String convId, final AgentStepPojo agentStep) {
    final long startTime = DateUtil.TIME.millis();
    logSetStart(convId, Collections.singletonList(agentStep));
    final String cacheKey = CommonCacheHelper.getAgentStepsCacheKey();
    Map<String, AgentStepPojo> cache =
        this.agentStepsCache.get(cacheKey, convId, new TypeReference<Map<String, AgentStepPojo>>() {
        });
    if (null == cache) {
      log.debug("initializing agent step cache with empty map for single call for conv id {}", convId);
      cache = new HashMap<>(); // intializing map for fresh conversation id
    }

    cache.put(agentStep.getId(), agentStep);
    this.agentStepsCache.put(cacheKey, convId, cache, this.cachettl);

    logSetEnd(convId, Collections.singletonList(agentStep), startTime);
    return new ArrayList<>(cache.values());
  }

  private static void logSetEnd(String convId, List<AgentStepPojo> agentSteps, long startTime) {
    if (log.isDebugEnabled()) {
      log.debug("added agent step with id {} to cache for conv id {}, time taken: {}ms",
          agentSteps.stream().map(AgentStepPojo::getId).collect(Collectors.joining(",")), convId,
          DateUtil.TIME.millis() - startTime);
    }
  }

  private static void logSetStart(String convId, List<AgentStepPojo> agentSteps) {
    if (log.isTraceEnabled()) {
      for (AgentStepPojo agentStep : agentSteps) {
        log.trace("agent step being added to cache for conv id {} - id '{}', text '{}'", convId,
            agentStep.getId(), agentStep.getText());
      }
    } else if (log.isDebugEnabled()) {
      log.debug("adding agent step to cache with ids {} to cache for conv id {}",
          agentSteps.stream().map(AgentStepPojo::getId).collect(Collectors.joining(",")), convId);
    }
  }

  public Map<String, AgentStepPojo> get(final String convId) {
    logGet(convId);
    return this.agentStepsCache.get(CommonCacheHelper.getAgentStepsCacheKey(), convId,
        new TypeReference<Map<String, AgentStepPojo>>() {
        });
  }

  public List<AgentStepPojo> getAsList(final String convId) {
    Map<String,AgentStepPojo> map = this.get(convId);
    if(null != map) {
      return new ArrayList<>(map.values());
    } else {
      return Collections.emptyList();
    }
  }

  private static void logGet(String convId) {
    if (log.isDebugEnabled()) {
      log.debug("fetching agent steps from cache for conv id: {}", convId);
    }
  }

  public void remove(final String convId) {
    final long startTime = DateUtil.TIME.millis();
    logRemoveStart(convId);
    this.agentStepsCache.delete(CommonCacheHelper.getAgentStepsCacheKey(), convId);
    logRemoveEnd(convId, startTime);
  }

  private static void logRemoveEnd(String convId, long startTime) {
    if (log.isDebugEnabled()) {
      log.debug("deleted cache entry of agent steps for conv id {} successfully in {}ms", convId,
          startTime - DateUtil.TIME.millis());
    }
  }

  private static void logRemoveStart(String convId) {
    if (log.isDebugEnabled()) {
      log.debug("deleting cache entry of agent steps for conv id {}", convId);
    }
  }

  public boolean delete(final String convId, final String id) {
    final String cacheKey = CommonCacheHelper.getAgentStepsCacheKey();
    Map<String, AgentStepPojo> cache =
        this.agentStepsCache.get(cacheKey, convId, new TypeReference<Map<String, AgentStepPojo>>() {
        });
    if (null == cache) {
      logNoCacheFound(convId);
      return false;
    }
    AgentStepPojo deletedPojo = cache.remove(id);
    if (null != deletedPojo) {
      this.agentStepsCache.put(cacheKey, convId, cache, this.cachettl); // update cache back
      log.debug("removed agent step with id {} from cache entry of conv id {}", id, convId);
      return true;
    }
    log.debug(
        "did not find agent step with id {} in cache for conv id {}, map.remove(id) returned null!",
        id, convId);
    return false;
  }

  public boolean update(final String convId, final String id, final String text) {
    if (log.isTraceEnabled()) {
      log.trace("updating text for conversation id {} in agent step with id {} with '{}' ", convId,
          id, text);
    } else if (log.isDebugEnabled()) {
      log.debug("updating text for conversation id {} in agent step with id {}", convId, id);
    }

    final String cacheKey = CommonCacheHelper.getAgentStepsCacheKey();
    Map<String, AgentStepPojo> cache =
        this.agentStepsCache.get(cacheKey, convId, new TypeReference<Map<String, AgentStepPojo>>() {
        });
    if (null == cache) {
      logNoCacheFound(convId);
      return false;
    }
    if (!cache.containsKey(id)) {
      log.error("could not find id {} in agent steps for conversation id {}", id, convId);
      return false;
    }

    if (log.isTraceEnabled()) {
      log.trace("old value of agent step with id {} is '{}'", id, cache.get(id).getText());
    }

    cache.get(id).setText(text);
    this.agentStepsCache.put(cacheKey, convId, cache, cachettl);

    if (log.isDebugEnabled()) {
      log.debug("updated text for agent step with id {} in conversation id {}", id, convId);
    }
    return true;
  }

  private static void logNoCacheFound(String convId) {
    log.warn("did not find cache entry for agent steps with conv id {}", convId);
  }

  public int getNextIndex(final String convId) {
    if (log.isDebugEnabled()) {
      log.debug("fetching current agent steps index number for conversation id {}", convId);
    }
    final String cacheKey = CommonCacheHelper.getAgentStepsCacheKey();
    Map<String, AgentStepPojo> cache =
        this.agentStepsCache.get(cacheKey, convId, new TypeReference<Map<String, AgentStepPojo>>() {
        });
    if (null == cache) {
      // since cache is empty at this time, the index can directly be returned as 1
      log.debug("current index for agent step of conversation id {} is 1 since the cache is empty", convId);
      return 1;
    }
    Optional<Integer> optIndex =
        cache.values().stream().map(AgentStepPojo::getIndex).max(Integer::compare);
    int nextIndex = 0;
    if (optIndex.isPresent()) {
      int currIndex = optIndex.get();
      if (log.isDebugEnabled()) {
        log.debug("current index for agent step of conversation id {} is {}", convId, currIndex);
      }
      nextIndex = currIndex;
    }
    nextIndex++;

    if (log.isDebugEnabled()) {
      log.debug("next index for agent steps in conversation id {} is {}", convId, nextIndex);
    }
    return nextIndex;
  }

  public boolean agentStepExist(final String convId, final String id) {
    final String cacheKey = CommonCacheHelper.getAgentStepsCacheKey();
    Map<String, AgentStepPojo> cache =
        this.agentStepsCache.get(cacheKey, convId, new TypeReference<Map<String, AgentStepPojo>>() {
        });
    if (null == cache) {
      logNoCacheFound(convId);
      return false;
    }
    return cache.containsKey(id);
  }

}
