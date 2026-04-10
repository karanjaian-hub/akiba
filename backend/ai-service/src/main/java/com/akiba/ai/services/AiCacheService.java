package com.akiba.ai.services;

import io.vertx.core.Future;
import io.vertx.redis.client.RedisAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * AiCacheService wraps Redis to cache AI responses.
 *
 * Why cache? Gemini API calls cost money and take ~1–3 seconds.
 * Many users ask very similar questions ("Am I overspending?").
 * Caching identical prompt hashes means we pay once and serve fast.
 *
 * TTL = 3600 seconds (1 hour). Financial data changes daily,
 * so we don't want stale answers living longer than that.
 */
public class AiCacheService {

  private static final Logger log = LoggerFactory.getLogger(AiCacheService.class);
  private static final int TTL_SECONDS = 3600;
  private static final String KEY_PREFIX = "ai:cache:";

  private final RedisAPI redis;

  public AiCacheService(RedisAPI redis) {
    this.redis = redis;
  }

  /**
   * Checks Redis for a cached response. Returns null on cache miss.
   */
  public Future<String> get(String systemPrompt, String userMessage) {
    String key = buildCacheKey(systemPrompt, userMessage);

    return redis.get(key).map(response -> {
      if (response == null) return null;
      log.debug("Cache HIT for key {}", key);
      return response.toString();
    });
  }

  /**
   * Stores an AI response in Redis with a 1-hour TTL.
   * We use SETEX = SET + EXPIRE in one atomic command.
   */
  public Future<Void> set(String systemPrompt, String userMessage, String aiResponse) {
    String key = buildCacheKey(systemPrompt, userMessage);

    return redis.setex(key, String.valueOf(TTL_SECONDS), aiResponse)
      .mapEmpty();
  }

  /**
   * Hashes the combined prompt so similar (but not identical) prompts
   * don't collide. SHA-256 gives us a 64-char hex string — compact
   * and collision-resistant enough for a cache key.
   */
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
      // SHA-256 is always available in the JDK — this can never happen.
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
