package com.faforever.loadtest.server.runner;

import com.faforever.loadtest.server.client.ClientSimulator;
import lombok.Data;

import java.util.Map;

@Data
public class Statistics {

  private final int elapsedMillis;
  private final int createdGames;
  private final int sentMessages;
  private final int receivedMessages;
  private final Map<ClientSimulator.State, Integer> clientStates;
  private final float incomingMessagesRate;
  private final float outgoingMessagesRate;
}
