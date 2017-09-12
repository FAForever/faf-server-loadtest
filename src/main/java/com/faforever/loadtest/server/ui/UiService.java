package com.faforever.loadtest.server.ui;

import javafx.fxml.FXMLLoader;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Service
public class UiService {

  private final ApplicationContext applicationContext;

  public UiService(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @SneakyThrows
  public <T extends Controller<?>> T loadFxml(String resource) {
    FXMLLoader loader = new FXMLLoader();
    loader.setControllerFactory(applicationContext::getBean);
    loader.setLocation(getClass().getResource(resource));
    loader.load();
    return loader.getController();
  }
}
