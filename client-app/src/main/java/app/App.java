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
import javafx.css.StyleableProperty;
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
        statusBar.updateStatus("Готово", statusBar.getColorDefault());
        HBox hBoxStatusBar = new HBox();
        hBoxStatusBar.setPadding(new Insets(0,0,0,10));
        hBoxStatusBar.getChildren().add(statusBar);
        hBoxStatusBar.getStyleClass().add("statusBar");
//        new Timeline(new KeyFrame(Duration.millis(1000), ));
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

    // запустить в потоке при старте приложения
//    private void statusBarSetText(String text, String codeColor) {
//
////        if (statusBarState[0].getValue() != text) {
////            statusBarState[0].setValue(text);
////        }
////        statusBar.getTextFill();
////        Platform.runLater(() -> {
////            statusBar.setText(text);
////            statusBar.setTextFill(Color.valueOf("0x0000ff"));
////        });
//    }

    private void listenPort() {
        int port;
        try {
            port = Integer.parseInt(inputPort.getText());
        } catch (NumberFormatException ex){
            statusBar.updateStatus("Порт должен быть целым числом", Paint.valueOf("red"));
            return;
        }
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            statusBar.updateStatus("Порт занят", Paint.valueOf("red"));
            return;
        }
        // сдулать паралельный поток на update statusBar
        // в функции statusBar проверять если новое состояние statesBar запускать поток

        threadListenUDP = new Thread(this::_listenPort);
        threadListenUDP.setName("listenUDP");
        threadListenUDP.setDaemon(true);
        runningListenUDP = true;

//        Task<Integer> task = new Task<Integer>() {
//            @Override protected Integer call() throws Exception {
//                int iterations = 0;
//                for (iterations = 0; iterations < 100000; iterations++) {
//                    if (isCancelled()) {
//                        break;
//                    }
//                    System.out.println("Iteration " + iterations);
//                }
//                return null;
//            }
//
//            @Override protected void succeeded() {
//                super.succeeded();
//                updateMessage("Done!");
//            }
//
//            @Override protected void cancelled() {
//                super.cancelled();
//                updateMessage("Cancelled!");
//            }
//
//            @Override protected void failed() {
//                super.failed();
//                updateMessage("Failed!");
//            }
//        };

        threadListenUDP.start();
//        statusBarSetText("Прием данных", "");
//        statusBar.updateStatus("Прием данных");
        inputPort.setDisable(true);
    }

    private void _listenPort() {
        boolean synched = false;
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (runningListenUDP) {
            statusBar.updateStatus("Прием данных", statusBar.getColorDefault());
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (socket.isClosed()) {
                    runningListenUDP = false;
                    continue;
                } else {
                    socket.close();
                }
            }
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
//        statusBarSetText("Прием данных остановлен", "");
        statusBar.updateStatusQueue("Прием данных остановлен", statusBar.getColorDefault());
        statusBar.updateStatusQueue("Готово", statusBar.getColorDefault());
        inputPort.setDisable(false);
    }

    private void dropTable() {
        tableList.clear();
        statusBar.updateStatusQueue("Таблица очищена", statusBar.getColorDefault());
//        if (runningListenUDP){
//            statusBarSetText("Прием данных", "");
//        }
    }
}

class StatusBarLabel extends Label {
    private final SimpleStringProperty statusText;
    private final SimpleObjectProperty<Paint> statusColor;
    private final ConcurrentLinkedDeque<StatusBarDto> statusQueue = new ConcurrentLinkedDeque<>();
    private final Paint colorDefault;
    private final Timeline timeline;

    public StatusBarLabel (){
        statusText = new SimpleStringProperty();
        colorDefault = getTextFill();
        statusColor = new SimpleObjectProperty<>();
        // bind
        textProperty().bind(statusText);
        textFillProperty().bind(statusColor);
        // thread
        timeline = new Timeline(new KeyFrame(Duration.millis(200),
                actionEvent -> {
            StatusBarDto last = null;
            if (statusQueue.size() > 1) {
                last = statusQueue.remove();
            } else {
                last = statusQueue.peek();
            }
            statusText.set(last.getText());
            statusColor.set(last.getColor());

        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // updQued  stop add que, play
    // updnow   clear que,stop, play

    public Paint getColorDefault(){
        return colorDefault;
    }

    public void updateStatus(String updText, Paint updColor){
        timeline.stop();
        statusQueue.clear();
        statusQueue.add(new StatusBarDto(updText, updColor));
        timeline.playFromStart();
    }

    public void updateStatusQueue(String updText, Paint updColor){
        statusQueue.add(new StatusBarDto(updText, Paint.valueOf("red")));
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
}