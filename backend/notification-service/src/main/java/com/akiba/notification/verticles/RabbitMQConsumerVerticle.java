package com.akiba.notification.verticles;

import com.akiba.notification.handlers.AlertHandler;
import com.akiba.notification.repositories.PreferencesRepository;
import com.akiba.notification.services.PushNotificationService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;

import java.util.UUID;

public class RabbitMQConsumerVerticle extends AbstractVerticle {

  private final RabbitMQClient         rabbit;
  private final AlertHandler           alertHandler;
  private final PreferencesRepository  prefsRepo;
  private final PushNotificationService pushService;

  public RabbitMQConsumerVerticle(
    RabbitMQClient rabbit,
    AlertHandler alertHandler,
    PreferencesRepository prefsRepo,
    PushNotificationService pushService
  ) {
    this.rabbit       = rabbit;
    this.alertHandler = alertHandler;
    this.prefsRepo    = prefsRepo;
    this.pushService  = pushService;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    rabbit.start()
      .compose(v -> Future.all(
        subscribePaymentCompleted(),
        subscribeBudgetAlert(),
        subscribeSavingsAlert(),
        subscribeReportReady(),
        subscribeDeadLetter()
      ))
      .onSuccess(v -> {
        System.out.println("[NotificationService] All RabbitMQ consumers running");
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("[NotificationService] Consumer startup failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  // ─── Consumers — Vert.x 5 API: basicConsumer() returns Future<RabbitMQConsumer>
  // then attach the handler via consumer.handler(msg -> { ... })

  private Future<Void> subscribePaymentCompleted() {
    return rabbit.basicConsumer("payment.completed")
      .onSuccess(consumer -> consumer.handler(msg -> {
        JsonObject body = msg.body().toJsonObject();
        UUID userId = UUID.fromString(body.getString("user_id"));
        String notifBody = String.format("Ksh %s sent to %s (%s)",
          body.getString("amount"),
          body.getString("recipient"),
          body.getString("category", "Transfer")
        );
        dispatchAlert(userId, "PAYMENT_CONFIRMED", "Payment Sent", notifBody, "payment_enabled");
      }))
      .mapEmpty();
  }

  private Future<Void> subscribeBudgetAlert() {
    return rabbit.basicConsumer("budget.alert")
      .onSuccess(consumer -> consumer.handler(msg -> {
        JsonObject body = msg.body().toJsonObject();
        UUID userId = UUID.fromString(body.getString("user_id"));
        String notifBody = String.format("You have used %s%% of your %s budget",
          body.getString("percentage"),
          body.getString("category")
        );
        dispatchAlert(userId, "BUDGET_EXCEEDED", "Budget Alert", notifBody, "budget_enabled");
      }))
      .mapEmpty();
  }

  private Future<Void> subscribeSavingsAlert() {
    return rabbit.basicConsumer("savings.alert")
      .onSuccess(consumer -> consumer.handler(msg -> {
        JsonObject body = msg.body().toJsonObject();
        UUID userId = UUID.fromString(body.getString("user_id"));
        String type = body.getString("type");

        if ("GOAL_COMPLETED".equals(type)) {
          String notifBody = String.format("Congratulations! %s achieved!", body.getString("goal_name"));
          dispatchAlert(userId, "GOAL_ACHIEVED", "Goal Complete!", notifBody, "savings_enabled");
        } else if ("BEHIND_PACE".equals(type)) {
          String notifBody = String.format("%s needs Ksh %s this week to stay on track",
            body.getString("goal_name"), body.getString("amount_needed"));
          dispatchAlert(userId, "SAVINGS_NUDGE", "Savings Reminder", notifBody, "savings_enabled");
        }
      }))
      .mapEmpty();
  }

  private Future<Void> subscribeReportReady() {
    return rabbit.basicConsumer("report.generate")
      .onSuccess(consumer -> consumer.handler(msg -> {
        JsonObject body = msg.body().toJsonObject();
        UUID userId = UUID.fromString(body.getString("user_id"));
        String notifBody = String.format("Your %s financial report is ready",
          body.getString("period", "monthly"));
        dispatchAlert(userId, "REPORT_READY", "Report Ready", notifBody, "reports_enabled");
      }))
      .mapEmpty();
  }

  private Future<Void> subscribeDeadLetter() {
    return rabbit.basicConsumer("dead.letter")
      .onSuccess(consumer -> consumer.handler(msg -> {
        System.err.println("[NotificationService] Dead-letter received: " + msg.body());
        UUID adminUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        dispatchAlert(adminUserId, "SYSTEM_ERROR", "System Alert",
          "A message failed processing. Check logs.", "system_enabled");
      }))
      .mapEmpty();
  }

  // ─── Shared dispatch: check prefs → save to DB → push to device ──────────

  private void dispatchAlert(UUID userId, String type, String title, String body, String prefKey) {
    prefsRepo.getPreferences(userId)
      .compose(prefs -> {
        boolean isEnabled = prefs.getBoolean(prefKey, true);
        if (!isEnabled) return Future.succeededFuture();

        return alertHandler.saveAlert(userId, type, title, body)
          .compose(saved -> pushService.send(prefs.getString("push_token"), title, body));
      })
      .onFailure(err -> System.err.println(
        "[NotificationService] dispatchAlert failed type=" + type + ": " + err.getMessage()
      ));
  }
}
