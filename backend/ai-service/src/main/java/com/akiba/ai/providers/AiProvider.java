package com.akiba.ai.providers;

import com.akiba.ai.models.Message;
import io.vertx.core.Future;

import java.util.List;

public interface AiProvider {

  /**
   * Sends a prompt to the underlying AI model and returns the response text.
   *
   * @param systemPrompt  The persona / context injected before the conversation.
   *                      This is where we'll put the user's financial snapshot.
   * @param userMessage   The actual message the user just typed.
   * @param history       Prior turns in the conversation (oldest → newest).
   *                      Empty list is fine for one-off insight calls.
   * @return Future<String> resolving to the model's reply text.
   */
  Future<String> complete(String systemPrompt, String userMessage, List<Message> history);
}
