package com.faforever.loadtest.server.runner;

import com.faforever.loadtest.server.client.ClientService;
import com.faforever.loadtest.server.client.ClientSimulator;
import com.faforever.loadtest.server.client.ThinkBehavior;
import com.google.common.base.Stopwatch;
import javafx.application.Platform;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LoadTestRunner {

  private final ClientService clientService;
  private final List<ClientSimulator> clients;
  private final ConcurrentMap<ClientSimulator.State, Integer> clientStates;
  private final Stopwatch stopwatch;

  private ExecutorService executor;
  private volatile boolean stop;
  private Statistics previousStatistics;
  private int numberOfClients;
  private InetSocketAddress serverAddress;
  private ThinkBehavior thinkBehavior;
  private AtomicInteger createdGames;
  private AtomicInteger sentMessages;
  private AtomicInteger receivedMessages;
  private ConcurrentMap<String, AtomicInteger> messagesByType;

  public LoadTestRunner(ClientService clientService) throws UnknownHostException {
    this.clientService = clientService;
    this.stop = true;
    clients = new ArrayList<>();
    clientStates = new ConcurrentHashMap<>();
    stopwatch = Stopwatch.createUnstarted();

    messagesByType = new ConcurrentHashMap<>();
  }

  @SneakyThrows
  public void start(String host, int port, ThinkBehavior thinkBehavior) {
    this.stop = false;
    this.thinkBehavior = thinkBehavior;
    serverAddress = new InetSocketAddress(Inet4Address.getByName(host), port);
    stopwatch.reset();
    stopwatch.start();

    clientStates.clear();
    for (ClientSimulator.State state : ClientSimulator.State.values()) {
      clientStates.put(state, 0);
    }

    createdGames = new AtomicInteger();
    sentMessages = new AtomicInteger();
    receivedMessages = new AtomicInteger();

    executor = createExecutor();
    executor.execute(() -> spawnClients(0, numberOfClients));
    log.info("Load test started");
  }

  private ExecutorService createExecutor() {
    return Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      return thread;
    });
  }

  private void spawnClients(int firstId, int numberOfClients) {
    Assert.state(!Platform.isFxApplicationThread(), "Must not run on application thread");
    if (Thread.interrupted()) {
      return;
    }
    for (int userId = firstId; userId < firstId + numberOfClients && !stop; userId++) {
      spawnClient(userId);
    }
  }

  private void spawnClient(int userId) {
    ClientSimulator client = clientService.createClientSimulator(userId, serverAddress, this.thinkBehavior, new ClientSimulator.ClientEventListener() {
      @Override
      public void onMessageReceived(String type) {
        messagesByType.computeIfAbsent(type, s -> new AtomicInteger()).incrementAndGet();
        receivedMessages.incrementAndGet();
      }

      @Override
      public void onMessageSent() {
        sentMessages.incrementAndGet();
      }

      @Override
      public void onGameCreated() {
        createdGames.incrementAndGet();
      }

      @Override
      public void onClienStopped(ClientSimulator client) {
        Optional.ofNullable(client.getState()).ifPresent(state -> clientStates.compute(state, (state1, integer) -> integer - 1));
      }

      @Override
      public void onStateChanged(ClientSimulator.State oldState, ClientSimulator.State state) {
        if (oldState != null) {
          clientStates.compute(oldState, (state1, integer) -> integer - 1);
        }
        clientStates.compute(state, (state1, integer) -> integer + 1);
      }
    });
    client.run();
    synchronized (clients) {
      clients.add(client);
    }
  }

  public void stop() {
    this.stop = true;
    Optional.ofNullable(executor).ifPresent(ExecutorService::shutdownNow);
    synchronized (clients) {
      clients.forEach(ClientSimulator::stop);
    }
    if (stopwatch.isRunning()) {
      stopwatch.stop();
    }
    clients.clear();
    previousStatistics = null;
    messagesByType.clear();
    log.info("Load test stopped");
  }

  public Statistics getStatistics() {
    int incomingMessagesRate = 0;
    int outgoingMessagesRate = 0;

    int elapsedMillis = (int) stopwatch.elapsed(TimeUnit.MILLISECONDS);
    int receivedMessages = this.receivedMessages.get();
    int sentMessage = sentMessages.get();
    if (previousStatistics != null) {
      int millisSinceLastStatistics = Math.max(elapsedMillis - previousStatistics.getElapsedMillis(), 1);
      incomingMessagesRate = (receivedMessages - previousStatistics.getReceivedMessages()) * 1000 / millisSinceLastStatistics;
      outgoingMessagesRate = (sentMessage - previousStatistics.getSentMessages()) * 1000 / millisSinceLastStatistics;
    }

    messagesByType.forEach((s, atomicInteger) -> log.debug("Received '{}': {}", s, atomicInteger.get()));
    log.debug("----------");

    previousStatistics = new Statistics(elapsedMillis, createdGames.get(), sentMessage, receivedMessages, clientStates, incomingMessagesRate, outgoingMessagesRate);
    return previousStatistics;
  }

  public void setNumberOfClients(int numberOfClients) {
    this.numberOfClients = numberOfClients;
    if (!isRunning()) {
      return;
    }

    executor.execute(() -> {
      int currentNumber;
      synchronized (clients) {
        currentNumber = clients.size();

        if (numberOfClients < currentNumber) {
          removeClients(currentNumber - numberOfClients);
        } else {
          spawnClients(currentNumber, numberOfClients - currentNumber);
        }
      }
    });
  }

  private boolean isRunning() {
    return !stop
        && executor != null
        && !executor.isTerminated();
  }

  private void removeClients(int numberOfClients) {
    synchronized (clients) {
      clients.stream()
          .sorted(Comparator.comparingInt(ClientSimulator::getId).reversed())
          .limit(numberOfClients)
          .collect(Collectors.toList())
          .forEach(clientSimulator -> {
            clientSimulator.stop();
            clients.remove(clientSimulator);
          });
    }
  }
}
