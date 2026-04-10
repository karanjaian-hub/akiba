package com.akiba.parsing.models;

import io.vertx.core.json.JsonObject;

/**
 * Represents a single financial transaction extracted from
 * an M-Pesa SMS or a bank PDF statement.
 *
 * Both parsers produce this same shape so downstream services
 * (CategoryService, transaction-service) never need to know the source.
 */
public class ParsedTransaction {

  public enum TransactionType { DEBIT, CREDIT }

  private String date;
  private double amount;
  private TransactionType type;
  private String merchant;      // M-Pesa: merchant/recipient name; Bank: description
  private String reference;     // M-Pesa transaction ID or bank reference number
  private String rawText;       // Original line — useful for debugging / audit
  private String category;      // Populated later by CategoryService
  private double balance;       // Bank statements include running balance; M-Pesa may not

  // --- Factories ---

  public static ParsedTransaction fromJson(JsonObject json) {
    ParsedTransaction tx = new ParsedTransaction();
    tx.date       = json.getString("date");
    tx.amount     = json.getDouble("amount", 0.0);
    tx.type       = TransactionType.valueOf(json.getString("type", "DEBIT").toUpperCase());
    tx.merchant   = json.getString("merchant", json.getString("description", ""));
    tx.reference  = json.getString("reference", "");
    tx.rawText    = json.getString("rawText", "");
    tx.category   = json.getString("category", "Other");
    tx.balance    = json.getDouble("balance", 0.0);
    return tx;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("date",      date)
      .put("amount",    amount)
      .put("type",      type.name())
      .put("merchant",  merchant)
      .put("reference", reference)
      .put("rawText",   rawText)
      .put("category",  category)
      .put("balance",   balance);
  }

  // --- Getters & Setters ---

  public String getDate()                        { return date; }
  public void   setDate(String date)             { this.date = date; }

  public double getAmount()                      { return amount; }
  public void   setAmount(double amount)         { this.amount = amount; }

  public TransactionType getType()               { return type; }
  public void   setType(TransactionType type)    { this.type = type; }

  public String getMerchant()                    { return merchant; }
  public void   setMerchant(String merchant)     { this.merchant = merchant; }

  public String getReference()                   { return reference; }
  public void   setReference(String reference)   { this.reference = reference; }

  public String getRawText()                     { return rawText; }
  public void   setRawText(String rawText)       { this.rawText = rawText; }

  public String getCategory()                    { return category; }
  public void   setCategory(String category)     { this.category = category; }

  public double getBalance()                     { return balance; }
  public void   setBalance(double balance)       { this.balance = balance; }
}
