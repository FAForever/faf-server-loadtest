package com.faforever.loadtest.server.client;

import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;

@Service
public class ClientService {

  private final JdbcTemplate jdbcTemplate;
  private final ApplicationContext applicationContext;

  public ClientService(JdbcTemplate jdbcTemplate, ApplicationContext applicationContext) {
    this.jdbcTemplate = jdbcTemplate;
    this.applicationContext = applicationContext;
  }

  @Transactional
  public ClientSimulator createClientSimulator(int userId, InetSocketAddress serverAddress, ThinkBehavior thinkBehavior, ClientSimulator.ClientEventListener clientEventListener) {
    User user = new User(userId, "User #" + userId, String.valueOf(userId));

    createUserIfNotExists(user);

    return applicationContext.getBean(ClientSimulator.class)
        .setServerAddress(serverAddress)
        .setThinkBehavior(thinkBehavior)
        .setClientEventListener(clientEventListener)
        .setUser(user);
  }

  private void createUserIfNotExists(User user) {
    String passwordHash = user.getPasswordHash();
    jdbcTemplate.update("INSERT INTO  login (login, password, email, steamid) VALUES (?, ?, ?, ?)" +
            "ON DUPLICATE KEY UPDATE password = ?",
        user.getUsername(), passwordHash, user.getEmail(), 76561198722330475L + user.getId(), passwordHash
    );
  }
}
