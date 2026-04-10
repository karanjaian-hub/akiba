package com.akiba.ai.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single turn in a conversation.
 *
 * role is always "user" or "model" — these are the exact strings
 * Gemini expects in its `contents` array.
 */
public class Message {

  public UUID id;
  public UUID conversationId;
  public String role;        // "user" | "model"
  public String content;
  public Instant createdAt;

  // No-arg constructor so Jackson / Vert.x JSON codec can deserialise us.
  public Message() {}

  public Message(String role, String content) {
    this.role = role;
    this.content = content;
  }
}
