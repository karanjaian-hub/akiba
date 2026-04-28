package com.akiba.payment.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single payment record in our system.
 * Status lifecycle: PENDING → COMPLETED | FAILED
 */
public class Payment {

  public enum Status { PENDING, COMPLETED, FAILED }
  public enum Type    { PHONE, TILL, PAYBILL }

  private UUID          id;
  private UUID          userId;
  private String        phone;           // recipient phone / till / paybill number
  private String        accountRef;      // account number (paybill only)
  private BigDecimal    amount;
  private String        category;
  private Type          type;
  private Status        status;
  private String        checkoutRequestId; // Daraja's identifier — used to match callbacks
  private String        merchantRequestId;
  private String        resultCode;
  private String        resultDesc;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // ── Getters & Setters ──────────────────────────────────────────────────────

  public UUID getId()                        { return id; }
  public void setId(UUID id)                 { this.id = id; }

  public UUID getUserId()                    { return userId; }
  public void setUserId(UUID userId)         { this.userId = userId; }

  public String getPhone()                   { return phone; }
  public void setPhone(String phone)         { this.phone = phone; }

  public String getAccountRef()              { return accountRef; }
  public void setAccountRef(String ref)      { this.accountRef = ref; }

  public BigDecimal getAmount()              { return amount; }
  public void setAmount(BigDecimal amount)   { this.amount = amount; }

  public String getCategory()                { return category; }
  public void setCategory(String category)   { this.category = category; }

  public Type getType()                      { return type; }
  public void setType(Type type)             { this.type = type; }

  public Status getStatus()                  { return status; }
  public void setStatus(Status status)       { this.status = status; }

  public String getCheckoutRequestId()             { return checkoutRequestId; }
  public void setCheckoutRequestId(String id)      { this.checkoutRequestId = id; }

  public String getMerchantRequestId()             { return merchantRequestId; }
  public void setMerchantRequestId(String id)      { this.merchantRequestId = id; }

  public String getResultCode()              { return resultCode; }
  public void setResultCode(String code)     { this.resultCode = code; }

  public String getResultDesc()              { return resultDesc; }
  public void setResultDesc(String desc)     { this.resultDesc = desc; }

  public LocalDateTime getCreatedAt()        { return createdAt; }
  public void setCreatedAt(LocalDateTime t)  { this.createdAt = t; }

  public LocalDateTime getUpdatedAt()        { return updatedAt; }
  public void setUpdatedAt(LocalDateTime t)  { this.updatedAt = t; }
}
