package com.faforever.loadtest.server.ui;

public interface Controller<ROOT> {

  ROOT getRoot();

  default void initialize() {
  }
}
