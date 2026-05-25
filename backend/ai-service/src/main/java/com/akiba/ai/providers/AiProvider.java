package com.akiba.ai.providers;

import com.akiba.ai.models.Message;
import io.vertx.core.Future;

import java.util.List;

public interface AiProvider {

  Future<String> complete(String systemPrompt, String userMessage, List<Message> history);
}
