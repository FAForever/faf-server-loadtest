package com.faforever.loadtest.server.ui;

import com.faforever.loadtest.server.client.ClientSimulator;
import com.faforever.loadtest.server.client.ThinkBehavior;
import com.faforever.loadtest.server.config.LoadTestProperties;
import com.faforever.loadtest.server.runner.LoadTestRunner;
import com.faforever.loadtest.server.runner.Statistics;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.StackedAreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import javafx.util.converter.NumberStringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainController implements Controller<Pane> {


  private static final NumberStringConverter INT_STRING_CONVERTER = new NumberStringConverter() {
    @Override
    public String toString(Number value) {
      return String.valueOf(value.intValue());
    }
  };
  private final LoadTestRunner loadTestRunner;
  private final LoadTestProperties properties;

  public Pane mainRoot;
  public Button startButton;
  public Button stopButton;
  public Label sentMessagesLabel;
  public Label receivedMessagesLabel;
  public StackedAreaChart<Number, Number> clientStatesChart;
  public LineChart<Number, Number> performanceChart;
  public ComboBox<ThinkBehavior> thinkBehaviorBox;
  public TextField hostField;
  public TextField portField;
  public TextField numberOfSecondsField;
  public Label createdGamesLabel;
  public Slider numberOfClientsSlider;
  public TextField numberOfClientsField;

  private Timeline updateTimeline;
  private Timeline terminateTimeline;

  public MainController(LoadTestRunner loadTestRunner, LoadTestProperties properties) {
    this.loadTestRunner = loadTestRunner;
    this.properties = properties;
  }

  @Override
  public Pane getRoot() {
    return mainRoot;
  }

  @Override
  public void initialize() {
    startButton.managedProperty().bind(startButton.visibleProperty());
    stopButton.managedProperty().bind(stopButton.visibleProperty());
    hostField.disableProperty().bind(stopButton.visibleProperty());
    portField.disableProperty().bind(stopButton.visibleProperty());
    numberOfSecondsField.disableProperty().bind(stopButton.visibleProperty());
    thinkBehaviorBox.disableProperty().bind(stopButton.visibleProperty());

    stopButton.visibleProperty().bind(startButton.visibleProperty().not());

    numberOfClientsSlider.valueProperty().addListener(this::onNumberOfClientsChanged);
    onNumberOfClientsChanged(numberOfClientsSlider.valueProperty());

    numberOfClientsField.textProperty().bindBidirectional(numberOfClientsSlider.valueProperty(), INT_STRING_CONVERTER);

    updateTimeline = new Timeline(
        new KeyFrame(Duration.ZERO, event -> update()),
        new KeyFrame(Duration.seconds(1))
    );
    updateTimeline.setCycleCount(Timeline.INDEFINITE);

    hostField.setText(properties.getServerAddress());
    portField.setText(String.valueOf(properties.getServerPort()));
    numberOfSecondsField.setText(String.valueOf(100));

    thinkBehaviorBox.getItems().setAll(ThinkBehavior.values());
    thinkBehaviorBox.getSelectionModel().select(properties.getThinkBehavior());
  }

  private void onNumberOfClientsChanged(Observable observable) {
    loadTestRunner.setNumberOfClients((int) numberOfClientsSlider.getValue());
  }

  private void update() {
    Statistics statistics = loadTestRunner.getStatistics();

    updateClientStateChart(statistics);
    updatePerformanceChart(statistics);

    sentMessagesLabel.setText(MessageFormat.format("{0}", statistics.getSentMessages()));
    receivedMessagesLabel.setText(MessageFormat.format("{0}", statistics.getReceivedMessages()));
    createdGamesLabel.setText(MessageFormat.format("{0}", statistics.getCreatedGames()));
  }

  @SuppressWarnings("unchecked")
  private void updatePerformanceChart(Statistics statistics) {
    ObservableList<XYChart.Series<Number, Number>> chartData = performanceChart.getData();

    if (chartData.isEmpty()) {
      XYChart.Series<Number, Number> incomingMessageRateSeries = new XYChart.Series<>();
      incomingMessageRateSeries.setName("Incoming");

      XYChart.Series<Number, Number> outgoingMessageRateSeries = new XYChart.Series<>();
      outgoingMessageRateSeries.setName("Outgoing");

      chartData.addAll(incomingMessageRateSeries, outgoingMessageRateSeries);
    }

    int elapsedSeconds = statistics.getElapsedMillis() / 1000;
    chartData.get(0).getData().add(new XYChart.Data<>(elapsedSeconds, statistics.getIncomingMessagesRate()));
    chartData.get(1).getData().add(new XYChart.Data<>(elapsedSeconds, statistics.getOutgoingMessagesRate()));
  }

  private void updateClientStateChart(Statistics statistics) {
    ObservableList<XYChart.Series<Number, Number>> chartData = clientStatesChart.getData();

    for (ClientSimulator.State state : ClientSimulator.State.values()) {
      int ordinal = state.ordinal();
      if (chartData.size() < ordinal + 1) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(state.toString());
        chartData.add(series);
      }

      int elapsedSeconds = statistics.getElapsedMillis() / 1000;
      Map<ClientSimulator.State, Integer> clientStates = statistics.getClientStates();

      ObservableList<XYChart.Data<Number, Number>> seriesData = chartData.get(ordinal).getData();
      XYChart.Data<Number, Number> data = new XYChart.Data<>(elapsedSeconds, clientStates.get(state), state);
      seriesData.add(data);
    }
  }

  public void onStartButtonClicked() {
    clientStatesChart.getData().clear();
    performanceChart.getData().clear();
    loadTestRunner.start(
        hostField.getText(),
        Integer.parseInt(portField.getText()),
        thinkBehaviorBox.getValue()
    );
    startButton.setVisible(false);
    updateTimeline.play();

    String numberOfSecondsFieldText = numberOfSecondsField.getText();
    if (!numberOfSecondsFieldText.isEmpty()) {
      terminateTimeline = new Timeline(
          new KeyFrame(Duration.seconds(Integer.parseInt(numberOfSecondsFieldText)), event -> onStopButtonClicked())
      );
      terminateTimeline.play();
    }
  }

  public void onStopButtonClicked() {
    Optional.ofNullable(terminateTimeline).ifPresent(Timeline::stop);
    Optional.ofNullable(updateTimeline).ifPresent(Timeline::stop);
    Optional.ofNullable(loadTestRunner).ifPresent(LoadTestRunner::stop);
    startButton.setVisible(true);
  }
}
