package com.akiba.ai.models;

import java.time.Instant;
import java.util.UUID;

public class Report {
  public UUID id;
  public UUID userId;
  public int month;         // 1–12
  public int year;
  public String content;    // Full AI-generated markdown report
  public String status;     // "GENERATING" | "COMPLETE" | "FAILED"
  public Instant createdAt;
}
