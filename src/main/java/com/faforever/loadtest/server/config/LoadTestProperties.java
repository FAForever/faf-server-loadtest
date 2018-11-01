package com.faforever.loadtest.server.config;

import com.faforever.loadtest.server.client.ThinkBehavior;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "loadtest", ignoreUnknownFields = false)
public class LoadTestProperties {

  private String serverAddress;
  private int serverPort;
  private ThinkBehavior thinkBehavior = ThinkBehavior.HUMAN;

  private int numberOfClients = 1000;
  private int testDurationSeconds = 360;
  private long lobbyMinTime = 5_000;
  private long lobbyMaxTime = 15 * 60_000;
  private long idleMinTime = 5_000;
  private long idleMaxTime = 60 * 60_000;
  private long gameMinTime = 2 * 60_000;
  private long gameMaxTime = 60 * 60_000;
  private long scoreScreenMinTime = 3_000;
  private long scoreScreenMaxTime = 30 * 60_000;
  private long gameStartupMinTime = 1_000;
  private long gameStartupMaxTime = 15_000;
}
