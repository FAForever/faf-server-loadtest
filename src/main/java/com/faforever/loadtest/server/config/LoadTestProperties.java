package com.faforever.loadtest.server.config;

import com.faforever.loadtest.server.client.ThinkBehavior;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "loadtest", ignoreUnknownFields = false)
public class LoadTestProperties {

  private int clients;
  private int seconds;
  private String serverAddress;
  private int serverPort;
  private ThinkBehavior thinkBehavior = ThinkBehavior.HUMAN;
}
