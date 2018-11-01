package com.faforever.loadtest.server.client;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;

@Service
public class ClientService {

  private final ApplicationContext applicationContext;

  public ClientService(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  public ClientSimulator createClientSimulator(int userId, InetSocketAddress serverAddress, ThinkBehavior thinkBehavior, ClientSimulator.ClientEventListener clientEventListener) {
    User user = new User(userId, "User #" + userId, String.valueOf(userId));

    return applicationContext.getBean(ClientSimulator.class)
        .setServerAddress(serverAddress)
        .setThinkBehavior(thinkBehavior)
        .setClientEventListener(clientEventListener)
        .setUser(user);
  }
}
