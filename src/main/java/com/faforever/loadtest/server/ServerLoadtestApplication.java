package com.faforever.loadtest.server;

import com.faforever.loadtest.server.config.LoadTestProperties;
import com.faforever.loadtest.server.ui.MainController;
import com.faforever.loadtest.server.ui.UiService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
@EnableConfigurationProperties(LoadTestProperties.class)
public class ServerLoadtestApplication extends Application {

  private static String[] args;
  private ApplicationContext applicationContext;

  public static void main(String[] args) throws InterruptedException {
    ServerLoadtestApplication.args = args;
    launch(args);
  }

  @Override
  public void init() throws Exception {
    applicationContext = new SpringApplicationBuilder(ServerLoadtestApplication.class)
        .run(args);
  }

  @Override
  public void start(Stage primaryStage) throws Exception {
    UiService uiService = applicationContext.getBean(UiService.class);

    MainController mainController = uiService.loadFxml("/main.fxml");
    Scene scene = new Scene(mainController.getRoot());
    scene.getStylesheets().setAll(getClass().getResource("/style.css").toExternalForm());
    primaryStage.setScene(scene);
    primaryStage.showingProperty().addListener((observable, oldValue, newValue) -> {
      if (!newValue) {
        mainController.onStopButtonClicked();
      }
    });
    primaryStage.show();
  }
}
