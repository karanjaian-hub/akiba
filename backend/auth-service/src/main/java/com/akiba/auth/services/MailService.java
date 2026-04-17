package com.akiba.auth.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;

public class MailService {

  private final MailClient mailClient;
  private final String from;

  public MailService(Vertx vertx) {
    this.from = System.getenv().getOrDefault("SMTP_FROM", "Akiba <karanjaian420@gmail.com>");

    MailConfig config = new MailConfig()
      .setHostname(System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"))
      .setPort(Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "587")))
      .setStarttls(StartTLSOptions.REQUIRED)
      .setUsername(System.getenv().getOrDefault("SMTP_USERNAME", "karanjaian420@gmail.com"))
      .setPassword(System.getenv().getOrDefault("SMTP_PASSWORD", ""));

    this.mailClient = MailClient.create(vertx, config);
  }

  // ─── Email Verification OTP ───────────────────────────────────────────────

  public Future<Void> sendVerificationOtp(String toEmail, String fullName, String otp) {
    MailMessage message = new MailMessage()
      .setFrom(from)
      .setTo(toEmail)
      .setSubject("Verify your Akiba account")
      .setHtml(verificationOtpHtml(fullName, otp))
      .setText(verificationOtpPlainText(fullName, otp));

    return mailClient.sendMail(message)
      .onSuccess(r -> System.out.println("[MailService] ✅ Verification OTP sent to " + toEmail))
      .onFailure(err -> System.err.println("[MailService] ❌ Failed to send verification OTP to " + toEmail + ": " + err.getMessage()))
      .mapEmpty();
  }

  // ─── Password Reset OTP ───────────────────────────────────────────────────

  public Future<Void> sendPasswordResetOtp(String toEmail, String fullName, String otp) {
    MailMessage message = new MailMessage()
      .setFrom(from)
      .setTo(toEmail)
      .setSubject("Akiba Password Reset Code")
      .setHtml(resetOtpHtml(fullName, otp))
      .setText(resetOtpPlainText(fullName, otp));

    return mailClient.sendMail(message)
      .onSuccess(r -> System.out.println("[MailService] ✅ Password reset OTP sent to " + toEmail))
      .onFailure(err -> System.err.println("[MailService] ❌ Failed to send reset OTP to " + toEmail + ": " + err.getMessage()))
      .mapEmpty();
  }

  // ─── Templates ────────────────────────────────────────────────────────────

  private String verificationOtpHtml(String fullName, String otp) {
    return """
      <html>
        <body style="font-family: Arial, sans-serif; color: #333; padding: 24px;">
          <h2 style="color: #2e7d32;">Welcome to Akiba, %s! 👋</h2>
          <p>Thanks for signing up. Use the code below to verify your email address:</p>
          <div style="margin: 24px 0; text-align: center;">
            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #2e7d32;">%s</span>
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

  private String verificationOtpPlainText(String fullName, String otp) {
    return """
      Welcome to Akiba, %s!

      Thanks for signing up. Use the code below to verify your email address:

      %s

      This code expires in 24 hours.

      Once verified you'll have full access to your Akiba account.

      If you didn't create this account, please ignore this email.
      """.formatted(fullName, otp);
  }

  private String resetOtpHtml(String fullName, String otp) {
    return """
      <html>
        <body style="font-family: Arial, sans-serif; color: #333; padding: 24px;">
          <h2 style="color: #2e7d32;">Password Reset Request</h2>
          <p>Hi %s,</p>
          <p>We received a request to reset your Akiba password. Use the code below:</p>
          <div style="margin: 24px 0; text-align: center;">
            <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #2e7d32;">%s</span>
          </div>
          <p>This code expires in <strong>10 minutes</strong>.</p>
          <p>If you did not request a password reset, you can safely ignore this email.</p>
          <br/>
          <p style="color: #888; font-size: 12px;">Do not share this code with anyone.</p>
        </body>
      </html>
      """.formatted(fullName, otp);
  }

  private String resetOtpPlainText(String fullName, String otp) {
    return """
      Hi %s,

      We received a request to reset your Akiba password.
      Your reset code is: %s

      This code expires in 10 minutes.

      If you did not request a password reset, you can safely ignore this email.
      Do not share this code with anyone.
      """.formatted(fullName, otp);
  }
}
