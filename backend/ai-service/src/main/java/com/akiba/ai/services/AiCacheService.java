package com.akiba.ai.services;

import io.vertx.core.Future;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class AiCacheService {

  private static final Logger log = LoggerFactory.getLogger(AiCacheService.class);
  private static final int TTL_SECONDS = 3600;
  private static final String KEY_PREFIX = "ai:cache:";

  private final RedisAPI redis;

  public AiCacheService(RedisAPI redis) {
    this.redis = redis;
  }

 // Checks Redis for a cached response. Returns null on cache miss.
  public Future<String> get(String systemPrompt, String userMessage) {
    String key = buildCacheKey(systemPrompt, userMessage);

    return redis.get(key).map(response -> {
      if (response == null) return null;
      log.debug("Cache HIT for key {}", key);
      return response.toString();
    });
  }

// Stores an AI response in Redis with a 1-hour TTL.
  public Future<Void> set(String systemPrompt, String userMessage, String aiResponse) {
    String key = buildCacheKey(systemPrompt, userMessage);

    return redis.setex(key, String.valueOf(TTL_SECONDS), aiResponse)
      .mapEmpty();
  }


  private String buildCacheKey(String systemPrompt, String userMessage) {
    String combined = systemPrompt + "|" + userMessage;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return KEY_PREFIX + hex;
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
