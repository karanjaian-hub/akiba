package com.akiba.ai.models;

import java.time.Instant;
import java.util.UUID;

public class Message {

  public UUID id;
  public UUID conversationId;
  public String role;        // "user" | "model"
  public String content;
  public Instant createdAt;

  public Message() {}

  public Message(String role, String content) {
    this.role = role;
    this.content = content;
  }
}
