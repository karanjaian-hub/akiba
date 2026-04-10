package com.akiba.ai.models;

import java.time.Instant;
import java.util.UUID;

public class Conversation {
  public UUID id;
  public UUID userId;
  public String title;      // Auto-generated from first message, e.g. "Where am I overspending?"
  public Instant createdAt;
  public Instant updatedAt;
}
