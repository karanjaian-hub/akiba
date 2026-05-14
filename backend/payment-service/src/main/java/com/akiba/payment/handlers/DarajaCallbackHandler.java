package com.akiba.payment.handlers;

import com.akiba.payment.services.PaymentService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class DarajaCallbackHandler {

  private final PaymentService paymentService;

  public DarajaCallbackHandler(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  public void handle(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null) {
      // Rule.. Always respond 200 to Daraja — if we return non-200, they retry endlessly
      ctx.response().setStatusCode(200).end();
      return;
    }

    try {
      JsonObject callback    = body.getJsonObject("Body").getJsonObject("stkCallback");
      String checkoutId      = callback.getString("CheckoutRequestID");
      String resultCode      = String.valueOf(callback.getInteger("ResultCode", -1));
      String resultDesc      = callback.getString("ResultDesc", "Unknown");

      paymentService.processCallback(checkoutId, resultCode, resultDesc)
        .onSuccess(v -> ctx.response().setStatusCode(200).end())
        .onFailure(err -> {
          // Log but still return 200 — so Daraja doesn't retry
          System.err.println("[payment-service] Callback processing error: " + err.getMessage());
          ctx.response().setStatusCode(200).end();
        });

    } catch (Exception e) {
      System.err.println("[payment-service] Malformed Daraja callback: " + e.getMessage()
        + " | Body: " + body.encode());
      ctx.response().setStatusCode(200).end(); // still 200 — don't let Daraja retry bad payloads
    }
  }
}
