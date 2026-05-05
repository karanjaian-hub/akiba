package com.akiba.notification.services;

import io.vertx.core.Future;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;

public class PushNotificationService {

  private final MailClient mailClient;
  private final String     fromAddress;

  public PushNotificationService(io.vertx.core.Vertx vertx) {
    String smtpHost = env("SMTP_HOST",     "smtp.gmail.com");
    int    smtpPort = Integer.parseInt(env("SMTP_PORT", "587"));
    String username = env("SMTP_USERNAME",  "");
    String password = env("SMTP_PASSWORD",  "");
    this.fromAddress = env("SMTP_FROM",     "noreply@akiba.app");

    MailConfig config = new MailConfig()
      .setHostname(smtpHost)
      .setPort(smtpPort)
      .setStarttls(StartTLSOptions.REQUIRED)
      .setUsername(username)
      .setPassword(password);

    this.mailClient = MailClient.create(vertx, config);
  }

  /**
   * Sends an email notification.
   * The "pushToken" field in preferences now stores the user's email address.
   * Returns a succeeded future even on failure — a failed email
   * should NOT roll back a DB save.
   */
  public Future<Void> send(String toEmail, String subject, String body) {
    if (toEmail == null || toEmail.isBlank()) {
      // User has no email registered yet — skip silently
      return Future.succeededFuture();
    }

    MailMessage message = new MailMessage()
      .setFrom(fromAddress)
      .setTo(toEmail)
      .setSubject(subject)
      .setText(body)
      .setHtml(buildHtml(subject, body));

    return mailClient.sendMail(message)
      .onFailure(err -> System.err.println("[EmailService] Failed to send to " + toEmail + ": " + err.getMessage()))
      .mapEmpty();
  }

  /** Wraps the plain text body in a minimal branded HTML template. */
  private String buildHtml(String subject, String body) {
    return """
            <html>
              <body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px;">
                <div style="background: #1B4F72; padding: 16px; border-radius: 8px 8px 0 0;">
                  <h1 style="color: #F0B429; margin: 0; font-size: 22px;">Akiba</h1>
                </div>
                <div style="background: #f9f9f9; padding: 24px; border-radius: 0 0 8px 8px;">
                  <h2 style="color: #1B4F72;">%s</h2>
                  <p style="color: #333; line-height: 1.6;">%s</p>
                </div>
                <p style="color: #999; font-size: 11px; text-align: center; margin-top: 16px;">
                  © Akiba. You are receiving this because you have an Akiba account.
                </p>
              </body>
            </html>
        """.formatted(subject, body);
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return (value != null && !value.isBlank()) ? value : defaultValue;
  }
}
