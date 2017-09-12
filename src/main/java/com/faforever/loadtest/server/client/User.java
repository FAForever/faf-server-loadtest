package com.faforever.loadtest.server.client;

import com.google.common.hash.Hashing;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simulated FAF user.
 */
@Data
public class User {

  private final int id;
  private final AtomicInteger receivedMessagesCount;

  public User(int id, String username, String password) {
    this.id = id;
    receivedMessagesCount = new AtomicInteger();
  }

  public String getUsername() {
    return "User " + id;
  }

  public String getPassword() {
    return "password" + id;
  }

  public String getEmail() {
    return "user" + id + "@example.com";
  }

  public String getPasswordHash() {
    return Hashing.sha256().hashString(getPassword(), StandardCharsets.UTF_8).toString();
  }
}
