package com.faforever.loadtest.server.client;

import com.faforever.loadtest.server.config.LoadTestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class ClientSimulator implements Runnable {

  public interface ClientEventListener {

    void onMessageReceived(String type);

    void onMessageSent();

    void onGameCreated();

    void onClienStopped(ClientSimulator client);

    void onStateChanged(State oldState, State state);
  }

  public enum State {
    DISCONNECTED, CONNECTING, CONNECTED, INITIATING_SESSION, LOGGING_IN, IDLE, CREATING_GAME, GAME_LOBBY, PLAYING, SCORE_SCREEN
  }

  private static final String JSON_STATS;

  static {
    try {
      JSON_STATS = Resources.toString(ClientSimulator.class.getResource("/game_stats_full.json"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final FafLegacyTcpClient tcpClient;
  private final ObjectMapper objectMapper;
  @Setter
  private ThinkBehavior thinkBehavior;
  @Setter
  private User user;
  @Setter
  private InetSocketAddress serverAddress;
  @Setter
  private ClientEventListener clientEventListener;
  private ScheduledThreadPoolExecutor executor;
  private OutputStream outputStream;
  private State state;
  private Map<String, Consumer<Map<String, Object>>> clientMessageHandlers;
  private Socket socket;
  private volatile boolean stop;
  private Thread serverReader;

  public ClientSimulator(FafLegacyTcpClient tcpClient, ObjectMapper objectMapper, LoadTestProperties properties) {
    this.tcpClient = tcpClient;
    this.objectMapper = objectMapper;
    this.clientMessageHandlers = new HashMap<>();

    clientMessageHandlers.put("session", this::onSession);
    clientMessageHandlers.put("social", this::noOp);
    clientMessageHandlers.put("welcome", this::onAuthenticationSuccess);
    clientMessageHandlers.put("authentication_failed", this::noOp);
    clientMessageHandlers.put("mod_info", this::noOp);
    clientMessageHandlers.put("notice", this::noOp);
    clientMessageHandlers.put("game_launch", this::onOpenGame);
    clientMessageHandlers.put("player_info", this::noOp);
    clientMessageHandlers.put("game_info", this::noOp);

  }

  @Override
  public void run() {
    Assert.state(user != null, "User must be set");
    Assert.state(serverAddress != null, "Server address must be set");
    Assert.state(thinkBehavior != null, "Think behavior must be set");
    Assert.state(clientEventListener != null, "Client event listener must be set");

    this.executor = new ScheduledThreadPoolExecutor(1, runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("client-worker-" + user.getId());
      thread.setDaemon(true);
      return thread;
    });

    changeState(null, State.DISCONNECTED);

    executor.submit(this::connect);
  }

  private void changeState(State oldState, State newState) {
    if (state != oldState) {
      throw new IllegalStateException("Expected to be in state " + oldState + " but was: " + state);
    }
    state = newState;
    clientEventListener.onStateChanged(oldState, newState);
  }

  private void onOpenGame(Map<String, Object> stringObjectMap) {
    clientEventListener.onGameCreated();
    openGame();

    executor.schedule(this::playGame, thinkTime(60_000, 300_000), TimeUnit.MILLISECONDS);
  }

  private void noOp(Map<String, Object> map) {

  }

  private void onAuthenticationSuccess(Map<String, Object> map) {
    changeState(State.LOGGING_IN, State.IDLE);
    executor.schedule(this::hostNewGame, thinkTime(10_000, 300_000), TimeUnit.MILLISECONDS);
  }

  private long thinkTime(int minMillis, int maxMillis) {
    switch (thinkBehavior) {
      case HUMAN:
        return (long) (Math.random() * (maxMillis - minMillis)) + minMillis;
      case FIXED:
        return minMillis;
      case BRUTE_FORCE:
        return 0;
      default:
        throw new IllegalStateException("Uncovered think behavior: " + thinkBehavior);
    }
  }

  private void onSession(Map<String, Object> map) {
    logIn();
  }

  private void logIn() {
    changeState(State.INITIATING_SESSION, State.LOGGING_IN);
    write(ImmutableMap.<String, Object>builder()
        .put("command", "hello")
        .put("login", user.getUsername())
        .put("password", user.getPasswordHash())
        .put("user_agent", "loadtest")
        .put("version", "1.0.0")
        .put("unique_id", "AnpaNjFpTERCV2pPQ2lidzY5UkNWZnc9PTZaNlVQMFFrc01iTWNtdEh5akE3NFdpUFVrSkVKWVV3UUV6SWc5ZGppRzhRNFEwNDRYTS93Sk40MFZxUkM5ZVdmM2xXVTVSTnlXZWMyd1lvUjQwUkpacE1qN21CN3l2citYbGNBRmhxVklnRHhtclMzMkF0bkhZYWF1emlUSFk5Zkw1WnNaaDBWWDFJcEY3L0lGa2lLUVd4V0xFVExoN0JUbVgzMUFTdWlUM1BBMS8rdkducnlDenQ3UFVNa2hVSnI1WVlBNTdMcllMZTZkd2E2K1BCcjNZWkRYblR1ZUNKdzY2d1M5U2p1MDBRTGV1Um1PdVBuNW1Uc1hpZyswcGtlemlDcEFFY1VacEkwK0Z0d094aFFUalFLUzNGaFNtQ1V5TVRkb29kY0dJZVU4VUkzWnFTS3JyU0FFeTNQNEFCYnNpOHRxNEFHMnN3UU5VczRielVtQ3VsMGVmelhYU1RyaE9haExMay91Q1g5bzhFb0Q5RzN0aUptbW9iQWE0M25US01EODd0VFlvenF4UlRoblVoSjBPQTlPYTYrbkxHbm43MXdoWUtYbWdJR2JadmYrOWVucTJHMU1vQVZwWFpkd0ZHNmlRYldlQ3ZQYlE3ZW1WVE82cVJHSVhEUHkzNGtBUkZqVXMrVjRBdzY4QUtDZXQ3RUYxMWc5eUY3MmVON1ZnTGtEa1N0dllkQkJxMTQxV0VXYUh4QXYwdDIvSVBoVmNnVWZFSkpJMktObnBldEQyaUYweW9qTnFLa05weFMwYkFmR0dhdjhpdHNoZ2VKckFKVlQzL0N4YWJmUFEzNGNRVkNMMU1tQnVNOUN4TGpZeEZpa3hlOGZKK3Y0MmRtU1ozY3JGUlhjMFFWaTFEM3l4VHpkenNXYU9XSFpYeGpQQ0F2ekN3RVpFdzB0NjFZZSt5dG5hd0JVR2dQdHFkYnk2S3RNeUwraEt0WUN3Snk3SkRIbTRSNDFnN01JVWN5WVVxVUExcW51MExrVXF3c0c2NEUxQmo0RVM1SGxwNjh5aVhvNnp6OTlzNUViNTVseVlDUUZOakRGUTBWalBZSlVpQkVpUFlCeElvcWo4eHdxcVUyWXJQQjA3dGJEd1hDbjYzdnZtSEYwTWV2a2hXdnhDMGNLUjZRZEJ2bVpPRisvZHc5anV6QTlvc2JnUFVtdzZCaTdvRHZjOW5RQ3I4YXNpLzF2QWVTdzU4ajFOUlJuOEFLSmNTcldwby9uWTZiVy9HU1NpWWkrSnl0T0tlU2UwMUhMYTh1Nm5KeG5aYkc1MkMzbDg1NFdCR3h4QnplbUc5UXc9PUI2dkdwMm5IRlIrQlpNRE1oaWhsV1BmQzVlQlhDU0RBYWNRQk93PT0")
        .put("local_ip", "127.0.0.1")
        .build());
  }

  @SneakyThrows
  private void write(Map<String, Object> map) {
    write(objectMapper.writeValueAsString(map));
  }

  @SneakyThrows
  private void write(String message) {
    clientEventListener.onMessageSent();
    log.trace("Sending: {}", message);
    tcpClient.write(outputStream, message);
  }

  @SneakyThrows
  public void connect() {
    if (state != State.DISCONNECTED) {
      return;
    }

    log.debug("Connecting");
    changeState(State.DISCONNECTED, State.CONNECTING);
    try {
      socket = new Socket(serverAddress.getAddress(), serverAddress.getPort());
    } catch (IOException e) {
      log.debug("Connection failed");
      changeState(State.CONNECTING, State.DISCONNECTED);
      if (!stop) {
        executor.schedule(ClientSimulator.this::connect, 3_000, TimeUnit.MILLISECONDS);
      }
      return;
    }
    outputStream = new BufferedOutputStream(socket.getOutputStream());
    InputStream inputStream = new BufferedInputStream(socket.getInputStream());
    changeState(State.CONNECTING, State.CONNECTED);
    log.debug("Connected");

    serverReader = new Thread("client-reader-" + user.getId()) {
      @Override
      public void run() {
        try {
          log.debug("Server reader started");
          while (!stop) {
            String message = tcpClient.read(inputStream);
            onServerMessage(message);
          }
        } catch (IOException e) {
          log.debug("Connection lost");
          changeState(state, ClientSimulator.State.DISCONNECTED);
          if (stop) {
            log.info("Client {} terminated", user.getId());
            clientEventListener.onClienStopped(ClientSimulator.this);
          } else {
            executor.schedule(ClientSimulator.this::connect, 3_000, TimeUnit.MILLISECONDS);
          }
        }
        log.debug("Server reader stopped");
      }
    };
    serverReader.setDaemon(true);
    serverReader.start();
    askSession();
  }

  @SneakyThrows
  @SuppressWarnings("unchecked")
  private void onServerMessage(String message) {
    log.trace("Received: {}", message);
    if ("PING".equals(message)) {
      write("PONG");
      return;
    }
    Map<String, Object> map = objectMapper.readValue(message, Map.class);
    clientEventListener.onMessageReceived((String) map.get("command"));

    String target = (String) map.getOrDefault("target", "client");
    switch (target) {
      case "client":
        handleClientMessage(map);
        break;

      case "game":
        handleGameMessage(map);
        break;
    }
  }

  private void askSession() {
    changeState(State.CONNECTED, State.INITIATING_SESSION);
    write(ImmutableMap.of(
        "version", "1.0.0",
        "user_agent", "loadtest",
        "command", "ask_session"
    ));
  }

  private void handleClientMessage(Map<String, Object> map) {
    String command = (String) map.get("command");
    Consumer<Map<String, Object>> handler = clientMessageHandlers.get(command);
    if (handler == null) {
      log.warn("No handler for: " + map);
      return;
    }
    handler.accept(map);
  }

  private void handleGameMessage(Map<String, Object> map) {
    // No-Op
  }

  private void closeGame() {
    changeState(State.SCORE_SCREEN, State.IDLE);
    sendGameState("Ended");
    executor.schedule(this::hostNewGame, thinkTime(30_000, 30_000), TimeUnit.MILLISECONDS);
  }

  private void sendGameState(String state) {
    write(ImmutableMap.of(
        "command", "GameState",
        "args", new Object[]{state},
        "target", "game"
    ));
  }

  private void playGame() {
    changeState(State.GAME_LOBBY, State.PLAYING);
    sendGameState("Launching");

    long timeTillGameEnd = 0;
    for (int playerId = 1; playerId <= 12; playerId++) {
      int finalPlayerId = playerId;
      long timeTillDeath = thinkTime(playerId * 30_000, playerId * 30_000 + 60_000);
      timeTillGameEnd = Math.max(timeTillDeath, timeTillGameEnd);
      executor.schedule(() -> letPlayerDie(finalPlayerId), timeTillDeath, TimeUnit.MILLISECONDS);
    }
    executor.schedule(this::endGame, timeTillGameEnd + 3_000, TimeUnit.MILLISECONDS);
  }

  private void endGame() {
    changeState(State.PLAYING, State.SCORE_SCREEN);
    executor.schedule(this::closeGame, thinkTime(3_000, 30_000), TimeUnit.MILLISECONDS);
  }

  private void letPlayerDie(int playerId) {
    sendGameResult(playerId, "score 10");
    sendGameResult(playerId, "victory");
    sendJsonStats(playerId);
  }

  private void sendJsonStats(int playerId) {
    write(ImmutableMap.of(
        "command", "JsonStats",
        "args", new Object[]{JSON_STATS},
        "target", "game"
    ));
  }

  private void sendGameResult(int playerId, String resultString) {
    write(ImmutableMap.of(
        "command", "GameResult",
        "args", new Object[]{playerId, resultString},
        "target", "game"
    ));
  }

  private void openGame() {
    changeState(State.CREATING_GAME, State.GAME_LOBBY);
    sendGameState("Idle");

    executor.schedule(() -> {
      sendGameState("Lobby");
      sendGameOption("UnitCap", "1000");
      sendGameOption("ShareUnitCap", "none");
      sendGameOption("FogOfWar", "explored");
      sendGameOption("Victory", "demoralization");
      sendGameOption("Timeouts", "3");
      sendGameOption("GameSpeed", "normal");
      sendGameOption("AllowObservers", 0);
      sendGameOption("CheatsEnabled", "false");
      sendGameOption("CivilianAlliance", "enemy");
      sendGameOption("RevealCivilians", "Yes");
      sendGameOption("PrebuiltUnits", "Off");
      sendGameOption("NoRushOption", "Off");
      sendGameOption("RandomMap", "Off");
      sendGameOption("Score", "no");
      sendGameOption("Share", "ShareUntilDeath");
      sendGameOption("TeamLock", "locked");
      sendGameOption("BuildMult", "2.0");
      sendGameOption("CheatMult", "2.0");
      sendGameOption("TMLRandom", "0");
      sendGameOption("LandExpansionsAllowed", "5");
      sendGameOption("NavalExpansionsAllowed", "4");
      sendGameOption("OmniCheat", "on");
      sendGameOption("ScenarioFile", "/maps/12 The Pass/12 The Pass_scenario.lua");
      sendGameOption("Slots", 12);

      executor.schedule(this::havePlayersJoin, thinkTime(10_000, 60_000), TimeUnit.MILLISECONDS);
    }, thinkTime(1000, 5000), TimeUnit.MILLISECONDS);
  }

  private void havePlayersJoin() {
    for (int playerId = 1; playerId <= 12; playerId++) {
      sendPlayerOption(playerId, "Faction", 1);
      sendPlayerOption(playerId, "Color", playerId);
      sendPlayerOption(playerId, "Team", playerId);
      sendPlayerOption(playerId, "StartSpot", playerId);

      for (int slotId = 1; slotId <= 12; slotId++) {
        clearSlot(slotId);
      }
    }
  }

  private void clearSlot(int armyId) {
    write(ImmutableMap.of(
        "command", "ClearSlot",
        "args", new Object[]{armyId},
        "target", "game"
    ));
  }

  private void sendPlayerOption(int playerId, String option, Object value) {
    write(ImmutableMap.of(
        "command", "PlayerOption",
        "args", new Object[]{String.valueOf(playerId), option, value},
        "target", "game"
    ));
  }

  private void sendGameOption(String option, Object value) {
    write(ImmutableMap.of(
        "command", "GameOption",
        "args", new Object[]{option, value},
        "target", "game"
    ));
  }

  private void hostNewGame() {
    changeState(State.IDLE, State.CREATING_GAME);
    write(ImmutableMap.<String, Object>builder()
        .put("mapname", "12 The Pass")
        .put("title", "Test Game " + user.getId())
        .put("mod", "faf")
        .put("options", new boolean[0])
        .put("access", "public")
        .put("visibility", "public")
        .put("command", "game_host")
        .build()
    );
  }

  public void stop() {
    stop = true;
    executor.shutdownNow();
    if (serverReader != null) {
      serverReader.interrupt();
    }
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        log.warn("Socket could not be closed", e);
      }
    }
  }

  public int getId() {
    return user.getId();
  }

  public State getState() {
    return state;
  }
}
