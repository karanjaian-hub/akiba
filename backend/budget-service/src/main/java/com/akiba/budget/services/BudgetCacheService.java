package com.akiba.budget.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;

import java.util.List;

public class BudgetCacheService {

  private static final int TTL_SECONDS = 300;
  private final RedisAPI redis;

  public BudgetCacheService(RedisAPI redis) {
    this.redis = redis;
  }

  private String cacheKey(String userId, String category, int month, int year) {
    return String.format("budget:%s:%s:%d:%d", userId, category, month, year);
  }

  public Future<JsonObject> get(String userId, String category, int month, int year) {
    return redis.get(cacheKey(userId, category, month, year))
      .map(response -> {
        if (response == null) return null;
        return new JsonObject(response.toString());
      })
      .otherwise((JsonObject) null);  // ← explicit cast resolves ambiguity
  }

  public Future<Void> set(String userId, String category, int month, int year, JsonObject data) {
    String key = cacheKey(userId, category, month, year);
    return redis.set(List.of(key, data.encode(), "EX", String.valueOf(TTL_SECONDS)))
      .<Void>mapEmpty()               // ← explicit type witness fixes Future<Object>
      .otherwise((Void) null);        // ← explicit cast resolves ambiguity
  }

  public Future<Void> invalidate(String userId, String category, int month, int year) {
    return redis.del(List.of(cacheKey(userId, category, month, year)))
      .<Void>mapEmpty()               // ← explicit type witness fixes Future<Object>
      .otherwise((Void) null);        // ← explicit cast resolves ambiguity
  }
}
