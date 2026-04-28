package com.akiba.payment.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A recipient the user has paid before — displayed in the "recent recipients" chips on the UI.
 * use_count drives the sort order (most used first).
 */
public class SavedRecipient {

  public enum Type { PHONE, TILL, PAYBILL }

  private UUID          id;
  private UUID          userId;
  private String        identifier;      // phone / till / paybill number
  private String        nickname;        // friendly label e.g. "Mama Mboga", "KPLC Token"
  private String        defaultCategory;
  private Type          type;
  private int           useCount;
  private LocalDateTime lastUsedAt;
  private LocalDateTime createdAt;

  // ── Getters & Setters ──────────────────────────────────────────────────────

  public UUID getId()                         { return id; }
  public void setId(UUID id)                  { this.id = id; }

  public UUID getUserId()                     { return userId; }
  public void setUserId(UUID userId)          { this.userId = userId; }

  public String getIdentifier()               { return identifier; }
  public void setIdentifier(String identifier){ this.identifier = identifier; }

  public String getNickname()                 { return nickname; }
  public void setNickname(String nickname)    { this.nickname = nickname; }

  public String getDefaultCategory()          { return defaultCategory; }
  public void setDefaultCategory(String cat)  { this.defaultCategory = cat; }

  public Type getType()                       { return type; }
  public void setType(Type type)              { this.type = type; }

  public int getUseCount()                    { return useCount; }
  public void setUseCount(int count)          { this.useCount = count; }

  public LocalDateTime getLastUsedAt()        { return lastUsedAt; }
  public void setLastUsedAt(LocalDateTime t)  { this.lastUsedAt = t; }

  public LocalDateTime getCreatedAt()         { return createdAt; }
  public void setCreatedAt(LocalDateTime t)   { this.createdAt = t; }
}
