package com.akiba.parsing.models;

import io.vertx.core.json.JsonObject;

/**
 * Tracks a single parse job from QUEUED → PROCESSING → DONE / FAILED.
 * Rows in parsing.jobs map 1-to-1 with this class.
 */
public class ParseJob {

  public enum Status { QUEUED, PROCESSING, DONE, FAILED }
  public enum JobType { MPESA_SMS, BANK_PDF }

  private String jobId;
  private String userId;
  private JobType type;
  private Status status;
  private String errorMessage;   // Null unless status == FAILED
  private long   createdAt;
  private long   updatedAt;

  public static ParseJob fromJson(JsonObject json) {
    ParseJob job = new ParseJob();
    job.jobId        = json.getString("jobId");
    job.userId       = json.getString("userId");
    job.type         = JobType.valueOf(json.getString("type", "MPESA_SMS").toUpperCase());
    job.status       = Status.valueOf(json.getString("status", "QUEUED").toUpperCase());
    job.errorMessage = json.getString("errorMessage");
    job.createdAt    = json.getLong("createdAt", System.currentTimeMillis());
    job.updatedAt    = json.getLong("updatedAt", System.currentTimeMillis());
    return job;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("jobId",        jobId)
      .put("userId",       userId)
      .put("type",         type.name())
      .put("status",       status.name())
      .put("errorMessage", errorMessage)
      .put("createdAt",    createdAt)
      .put("updatedAt",    updatedAt);
  }

  // --- Getters & Setters ---

  public String  getJobId()                         { return jobId; }
  public void    setJobId(String jobId)             { this.jobId = jobId; }

  public String  getUserId()                        { return userId; }
  public void    setUserId(String userId)           { this.userId = userId; }

  public JobType getType()                          { return type; }
  public void    setType(JobType type)              { this.type = type; }

  public Status  getStatus()                        { return status; }
  public void    setStatus(Status status)           { this.status = status; }

  public String  getErrorMessage()                  { return errorMessage; }
  public void    setErrorMessage(String errorMessage){ this.errorMessage = errorMessage; }

  public long    getCreatedAt()                     { return createdAt; }
  public void    setCreatedAt(long createdAt)       { this.createdAt = createdAt; }

  public long    getUpdatedAt()                     { return updatedAt; }
  public void    setUpdatedAt(long updatedAt)       { this.updatedAt = updatedAt; }
}
