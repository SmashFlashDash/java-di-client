package app;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableStringProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class App extends Application {
    Stage window;
    TableView<PackageSin> table;
    TextField inputPort;
    Button startButton, stoptButton, dropButton;
    StatusBarLabel statusBar;
    Thread threadListenUDP;
    Boolean runningListenUDP = false;
    private DatagramSocket socket;
    private final byte[] buf = new byte[256];
    private final ByteBuffer frameBuf = ByteBuffer.allocate(26 * 2).order(ByteOrder.LITTLE_ENDIAN);
    private ObservableList<PackageSin> tableList;

    // --module-path "C:\Program Files\Java\javafx-sdk-19\lib" --add-modules javafx.controls,javafx.fxml
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        window = primaryStage;
        window.setTitle("get TMI");
        // NUmberPackage
        TableColumn<PackageSin, Integer> idColumn = new TableColumn<>("Номер пакета");
        idColumn.setMinWidth(200);
        idColumn.setCellValueFactory(new PropertyValueFactory<>("counter"));
        // DatePackage
        TableColumn<PackageSin, Integer> dateColumn = new TableColumn<>("Время");
        dateColumn.setMinWidth(200);
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("localDateTime"));
        // anglePackage
        TableColumn<PackageSin, Integer> angleSinColumn  = new TableColumn<>("Угол синус");
        angleSinColumn.setMinWidth(200);
        angleSinColumn.setCellValueFactory(new PropertyValueFactory<>("angleSin"));
        // crc16
        TableColumn<PackageSin, Integer> crc16Column  = new TableColumn<>("CRC16");
        crc16Column.setMinWidth(200);
        crc16Column.setCellValueFactory(new PropertyValueFactory<>("crc16"));
        // table
        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().addAll(Arrays.asList(idColumn, dateColumn, angleSinColumn, crc16Column));
        tableList = table.getItems();
        final PseudoClass errors = PseudoClass.getPseudoClass("errors");
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(PackageSin item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(errors, (item != null) && item.isHaveErrors());
            }
        });
        table.setOnScrollStarted(e -> { table.scrollTo(tableList.size() - 1);});
        // prot field
        Label lblPort = new Label("Port");
        lblPort.setFont(new Font(20));
        inputPort = new TextField();
        inputPort.setPromptText("Port");
        inputPort.setText("15000");
        inputPort.setMinWidth(100);
        // buttons
        startButton = new Button("Start");
        startButton.setOnAction(e -> listenPort());
        stoptButton = new Button("Stop");
        stoptButton.setOnAction(e -> stopPort());
        dropButton = new Button("Drop");
        dropButton.setOnAction(e -> dropTable());
        // layout
        HBox hBox = new HBox();
        hBox.setPadding(new Insets(10,10,10,10));
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(lblPort, inputPort, startButton, stoptButton, dropButton);
        // statusBar
        statusBar = new StatusBarLabel();
        statusBar.updateStatus(new StatusBarDto("Готово", statusBar.getColorDefault()));
        HBox hBoxStatusBar = new HBox();
        hBoxStatusBar.setPadding(new Insets(0,0,0,10));
        hBoxStatusBar.getChildren().add(statusBar);
        hBoxStatusBar.getStyleClass().add("statusBar");
        // main panel
        VBox vBox = new VBox();
        vBox.setPadding(Insets.EMPTY);
        vBox.getChildren().addAll(table, hBox, hBoxStatusBar);
        VBox.setVgrow(table, Priority.ALWAYS);


        Scene scene = new Scene(vBox);
        scene.getStylesheets().add("styles.css");
        window.onCloseRequestProperty().setValue(e -> {
            if (runningListenUDP){
                stopPort();
            }
        });
        window.setScene(scene);
        window.show();
    }

    private void listenPort() {
        int port;
        try {
            port = Integer.parseInt(inputPort.getText());
        } catch (NumberFormatException ex){
            statusBar.updateStatusNow(new StatusBarDto("Порт должен быть целым числом", Paint.valueOf("red")));
            return;
        }
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            if (runningListenUDP) {
                statusBar.updateStatusNow(new StatusBarDto("Порт запущен", Paint.valueOf("red")));
            } else {
                statusBar.updateStatusNow(new StatusBarDto("Порт занят", Paint.valueOf("red")));
            }
            return;
        }
        threadListenUDP = new Thread(this::_listenPort);
        threadListenUDP.setName("listenUDP");
        threadListenUDP.setDaemon(true);
        runningListenUDP = true;
        threadListenUDP.start();
        inputPort.setDisable(true);
    }

    private void _listenPort() {
        boolean synched = false;
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (runningListenUDP) {
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (socket.isClosed()) {
                    runningListenUDP = false;
                    statusBar.updateStatusNow(new StatusBarDto("Порт закрыт", statusBar.getColorDefault()));
                    continue;
                } else {
                    socket.close();
                }
            }
            statusBar.updateStatus(new StatusBarDto("Прием данных", statusBar.getColorDefault()));
            frameBuf.put(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()));
            frameBuf.flip();
            if (!synched) {
                for (int i = 0; i < frameBuf.limit() - 3; i++) {
                    frameBuf.position(i);
                    if (PackageSin.validateSynch(frameBuf, frameBuf.position())) {
                        synched = true;
                        break;
                    }
                }
            }
            if (synched && frameBuf.remaining() >= 26) {
                byte[] bytes = new byte[26];
                frameBuf.get(bytes);
                PackageSin data = new PackageSin(bytes);
                if (tableList.size() > 0)
                    data.validateField(data.getCounter() - 1, tableList.get(tableList.size() - 1).getCounter());
                tableList.add(data);
                if (data.isHaveErrors()){
                    synched = false;
                }
            }
            frameBuf.compact();
        }
    }

    private void stopPort() {
        runningListenUDP = false;
        if (threadListenUDP != null) {
            try {
                socket.close();
                threadListenUDP.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        statusBar.updateStatusQueue(Arrays.asList(
                new StatusBarDto("Прием данных остановлен", statusBar.getColorDefault()),
                new StatusBarDto("Готово", statusBar.getColorDefault())));
        inputPort.setDisable(false);
    }

    private void dropTable() {
        tableList.clear();
        statusBar.updateStatusNow(new StatusBarDto("Таблица очищена", statusBar.getColorDefault()));
    }
}

class StatusBarLabel extends Label {
    private final SimpleStringProperty statusText;
    private final SimpleObjectProperty<Paint> statusColor;
    private final ConcurrentLinkedDeque<StatusBarDto> statusQueue = new ConcurrentLinkedDeque<>();
    private Paint colorDefault;
    private final Timeline timeline;

    public StatusBarLabel (){
        statusText = new SimpleStringProperty();
        colorDefault = getTextFill();
        statusColor = new SimpleObjectProperty<>();
        // bind
        textProperty().bind(statusText);
        textFillProperty().bind(statusColor);
        // thread
        timeline = new Timeline(new KeyFrame(Duration.millis(800),
                actionEvent -> {
            if (!statusQueue.isEmpty()){
                StatusBarDto last = statusQueue.remove();
                statusText.set(last.getText());
                statusColor.set(last.getColor());
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    public Paint getColorDefault(){
        return colorDefault;
    }

    public void setColorDefault(Paint color){
        colorDefault = color;
    }

    public void updateStatusNow(StatusBarDto statusBarDto){
        timeline.stop();
        statusQueue.clear();
        statusQueue.add(statusBarDto);
        timeline.playFromStart();
    }

    public void updateStatus(StatusBarDto statusBarDto){
        if (!statusQueue.contains(statusBarDto)){
            timeline.stop();
            statusQueue.add(statusBarDto);
            timeline.playFromStart();
        }
    }

    public void updateStatusQueue(List<StatusBarDto> statuses){
        timeline.stop();
        statusQueue.clear();
        statusQueue.addAll(statuses);
        timeline.playFromStart();
    }
}

class StatusBarDto{
    private final String text;
    private final Paint color;

    public StatusBarDto(String text, Paint color) {
        this.text = text;
        this.color = color;
    }

    public String getText() {
        return text;
    }

    public Paint getColor() {
        return color;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusBarDto that = (StatusBarDto) o;
        return Objects.equals(text, that.text) &&
                Objects.equals(color, that.color);
    }

//    @Override
//    public int hashCode() {
//        return Objects.hash(text, color);
//    }
}