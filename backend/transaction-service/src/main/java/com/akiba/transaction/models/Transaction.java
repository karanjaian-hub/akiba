// Transaction.java
// package com.akiba.transaction.models

package com.akiba.transaction.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Transaction {

  private UUID id;
  private UUID userId;
  private LocalDateTime date;
  private BigDecimal amount;
  private String type;       // DEBIT or CREDIT
  private String merchant;
  private String category;
  private String source;     // MPESA_SMS, BANK_PDF, MANUAL
  private String reference;
  private String rawText;
  private boolean anomalous;
  private LocalDateTime createdAt;

  // Getters and setters omitted for brevity — generate via IDE (Alt+Insert → Getters/Setters)
}
