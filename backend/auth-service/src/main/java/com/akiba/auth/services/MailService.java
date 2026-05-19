package com.akiba.auth.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class MailService {

  private final WebClient webClient;
  private final String apiKey;
  private final String from;
  private final String fromName;

  public MailService(Vertx vertx) {
    this.apiKey   = System.getenv().getOrDefault("BREVO_API_KEY", "");
    this.from     = System.getenv().getOrDefault("SMTP_FROM", "karanjaian420@gmail.com");
    this.fromName = "Akiba";

    this.webClient = WebClient.create(vertx, new WebClientOptions()
      .setSsl(true)
      .setTrustAll(true)
      .setDefaultHost("api.brevo.com")
      .setDefaultPort(443));
  }

  public Future<Void> sendVerificationOtp(String toEmail, String fullName, String otp) {
    JsonObject body = new JsonObject()
      .put("sender", new JsonObject().put("name", fromName).put("email", from))
      .put("to", new JsonArray().add(new JsonObject().put("email", toEmail).put("name", fullName)))
      .put("subject", "Verify your Akiba account")
      .put("htmlContent", verificationOtpHtml(fullName, otp));

    return send(body, toEmail, "Verification OTP");
  }

  public Future<Void> sendPasswordResetOtp(String toEmail, String fullName, String otp) {
    JsonObject body = new JsonObject()
      .put("sender", new JsonObject().put("name", fromName).put("email", from))
      .put("to", new JsonArray().add(new JsonObject().put("email", toEmail).put("name", fullName)))
      .put("subject", "Akiba Password Reset Code")
      .put("htmlContent", resetOtpHtml(fullName, otp));

    return send(body, toEmail, "Password Reset OTP");
  }

  private Future<Void> send(JsonObject body, String toEmail, String type) {
    return webClient.post("/v3/smtp/email")
      .putHeader("api-key", apiKey)
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(body)
      .compose(response -> {
        if (response.statusCode() == 200 || response.statusCode() == 201) {
          System.out.println("[MailService] ✅ " + type + " sent to " + toEmail);
          return Future.succeededFuture();
        } else {
          String err = response.bodyAsString();
          System.err.println("[MailService] ❌ Failed to send " + type + " to " + toEmail + ": " + err);
          return Future.failedFuture("Email send failed: " + err);
        }
      });
  }

  private String verificationOtpHtml(String fullName, String otp) {
    return """
      <html>
        <body style="font-family: Arial, sans-serif; color: #333; padding: 24px;">
          <h2 style="color: #4C1D95;">Welcome to Akiba, %s! 👋</h2>
          <p>Thanks for signing up. Use the code below to verify your email address:</p>
          <div style="margin: 24px 0; text-align: center;">
            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #4C1D95;">%s</span>
          </div>
          <p>This code expires in <strong>24 hours</strong>.</p>
          <p>Once verified you'll have full access to your Akiba account.</p>
          <br/>
          <p style="color: #888; font-size: 12px;">
            If you didn't create this account, please ignore this email.
          </p>
        </body>
      </html>
      """.formatted(fullName, otp);
  }

  private String resetOtpHtml(String fullName, String otp) {
    return """
      <html>
        <body style="font-family: Arial, sans-serif; color: #333; padding: 24px;">
          <h2 style="color: #4C1D95;">Password Reset Request</h2>
          <p>Hi %s,</p>
          <p>We received a request to reset your Akiba password. Use the code below:</p>
          <div style="margin: 24px 0; text-align: center;">
            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #4C1D95;">%s</span>
          </div>
          <p>This code expires in <strong>10 minutes</strong>.</p>
          <p>If you did not request a password reset, you can safely ignore this email.</p>
          <br/>
          <p style="color: #888; font-size: 12px;">Do not share this code with anyone.</p>
        </body>
      </html>
      """.formatted(fullName, otp);
  }
}
