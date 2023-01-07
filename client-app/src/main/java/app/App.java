package app;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ScrollEvent;
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
import java.util.Arrays;
import java.util.List;

public class App extends Application {
    private final byte[] buf = new byte[256];
    private final ByteBuffer frameBuf = ByteBuffer.allocate(26 * 2).order(ByteOrder.LITTLE_ENDIAN);
    Stage window;
    private TableView<PackageSin> table;
    private ScrollBar tableVs;
    private Timeline threadTableAutoScroll = null;
    private TextField inputPort;
    Button startButton, stoptButton, dropButton;
    private StatusBarLabel statusBar;
    private Thread threadListenUDP;
    private Boolean runningListenUDP = false;
    private DatagramSocket socket;
    private ObservableList<PackageSin> tableList;

    // --module-path "C:\Program Files\Java\javafx-sdk-19\lib" --add-modules javafx.controls,javafx.fxml
    public static void main(String[] args) {
        launch(args);
    }

    // TODO: процент ширины на колонки
    // addListiner autoscroll когда скролл внизу
    // переделать thread на javaFx Task чтобы управлять gui
    @Override
    public void start(Stage primaryStage) {
        window = primaryStage;
        window.setTitle("get TMI");
        // NUmberPackage
        TableColumn<PackageSin, Integer> idColumn = new TableColumn<>("Номер пакета");
        idColumn.setMinWidth(10);
//        idColumn.setPrefWidth(200);
//        idColumn.setW
        idColumn.setCellValueFactory(new PropertyValueFactory<>("counter"));
        // DatePackage
        TableColumn<PackageSin, Integer> dateColumn = new TableColumn<>("Время");
        dateColumn.setMinWidth(10);
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("localDateTime"));
        // anglePackage
        TableColumn<PackageSin, Integer> angleSinColumn = new TableColumn<>("Угол [гр. \u00B0]");
        angleSinColumn.setMinWidth(10);
        angleSinColumn.setCellValueFactory(new PropertyValueFactory<>("angleSin"));
        // crc16
        TableColumn<PackageSin, Integer> crc16Column = new TableColumn<>("CRC16");
        crc16Column.setMinWidth(10);
        crc16Column.setCellValueFactory(new PropertyValueFactory<>("crc16"));
        // table
        table = new TableView<>();
        table.setFixedCellSize(20);
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
        hBox.setPadding(new Insets(10, 10, 10, 10));
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(lblPort, inputPort, startButton, stoptButton, dropButton);
        // statusBar
        statusBar = new StatusBarLabel();
        statusBar.updateStatus("Готово");
        HBox hBoxStatusBar = new HBox();
        hBoxStatusBar.setPadding(new Insets(0, 0, 0, 10));
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
            if (runningListenUDP) {
                stopPort();
            }
        });
        window.setScene(scene);
        window.show();


        // TODO: вариант установить событие на scroll mouseRelease
        // autoscroll
//        for (Node node : list.lookupAll(".scroll-bar")) {
//            if (node instanceof ScrollBar) {
//                ScrollBar bar = (ScrollBar) node;
//                if (bar.getOrientation().equals(Orientation.VERTICAL)) {
//                    scrollbar = bar;
//                }
//            }
//        }
        tableVs = (ScrollBar) table.lookup(".scroll-bar:vertical"); // сделать через стрим lookupAll
        // этот метод бросает ошибку когд много строк в tableList,
        // т.к. вычисляет координаты скрола по сайзам колонок и их количеству
        // threadTableAutoScroll = new Timeline(new KeyFrame(Duration.millis(1000), e -> {table.scrollTo(tableList.size() - 1);
        threadTableAutoScroll = new Timeline(new KeyFrame(Duration.millis(200), e -> {tableVs.setValue(1d);
            System.out.println("скролл завершен");
        }));
        threadTableAutoScroll.setCycleCount(Timeline.INDEFINITE);
        threadTableAutoScroll.play();
        tableVs.setOnMouseReleased(e -> autoScroll());
        tableVs.setOnScroll(e -> autoScroll());

        // TODO: второй вариант не запускать timeline поток autoscrol а смещаться только если был доабвлен новый item
//        tableList.addListener((ListChangeListener<PackageSin>) (ch -> {
//            ch.next();
//            final int size = table.getItems().size();
//            ScrollBar verticalBar = (ScrollBar) table.lookup(".scroll-bar:vertical");
//            verticalBar.getValue();
//            verticalBar.getMax();
//            if (size > 0) {
//                Platform.runLater( () -> table.scrollTo(tableList.size()-1) );
//            }
//        }));

        // TODO: установить разную ширину для колонок
//        idColumn.prefWidthProperty().bind(table.widthProperty().multiply(0.5));
//        crc16Column.prefWidthProperty().bind(table.widthProperty().multiply(0.5));
    }

    private void autoScroll(){
        if (threadTableAutoScroll.getStatus() != Animation.Status.RUNNING && tableVs.getValue() == 1d){
            threadTableAutoScroll.play();
        }
        else {
            threadTableAutoScroll.stop();
        }
    }

    private void listenPort() {
        int port;
        try {
            port = Integer.parseInt(inputPort.getText());
        } catch (NumberFormatException ex) {
            statusBar.updateStatus(new StatusBarDto("Порт должен быть цифрой", statusBar.RED));
            return;
        }
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            statusBar.updateStatus(new StatusBarDto("Порт занят", statusBar.RED));
            if (runningListenUDP) {
                statusBar.updateStatusTimeLine(new StatusBarDto("Прием данных", statusBar.DEF));
            }
            return;
        }
        threadListenUDP = new Thread(this::_listenPort);
        threadListenUDP.setName("listenUDP");
        threadListenUDP.setDaemon(true);
        runningListenUDP = true;
        threadListenUDP.start();
        statusBar.updateStatus(new StatusBarDto("Прием данных", statusBar.DEF));
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
                } else {
                    runningListenUDP = false;
                    socket.close();
                }
                continue;
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
                if (data.isHaveErrors()) {
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
        statusBar.updateStatus(new StatusBarDto("Прием данных остановлен", statusBar.DEF));
        statusBar.updateStatusTimeLine(new StatusBarDto("Готово", statusBar.DEF));
        inputPort.setDisable(false);
    }

    private void dropTable() {
        tableList.clear();
        statusBar.updateStatus(new StatusBarDto("Таблица очищена", statusBar.DEF));
        if (runningListenUDP) {
            statusBar.updateStatusTimeLine(new StatusBarDto("Прием данных", statusBar.DEF));
        }
    }
}

class StatusBarLabel extends Label {
    private final Timeline timeline;
    private final ObservableList<KeyFrame> timelineFrames;
    private final int durationMills = 400;
    public Paint RED = Color.RED;
    public Paint DEF = Color.web("0xffffffff");

    public StatusBarLabel() {
        timeline = new Timeline();
        timelineFrames = timeline.getKeyFrames();
    }

    public void updateStatus(String text) {
        timeline.stop();
        timelineFrames.clear();
        setText(text);
    }

    public void updateStatus(StatusBarDto status) {
        timeline.stop();
        timelineFrames.clear();
        setText(status.getText());
        setTextFill(status.getPaint());
    }

    public void updateStatusTimeLine(StatusBarDto status) {
        timeline.pause();
        timelineFrames.clear();
        timelineFrames.add(new KeyFrame(Duration.millis(durationMills), ev -> {
            setText(status.getText());
            setTextFill(status.getPaint());
        }));
        timeline.play();
    }

    public void updateStatusTimeLine(List<StatusBarDto> statuses) {
        timeline.stop();
        timelineFrames.clear();
        for (StatusBarDto s : statuses) {
            timelineFrames.add(new KeyFrame(Duration.millis(durationMills), ev -> {
                setText(s.getText());
                setTextFill(s.getPaint());
            }));
        }
        timeline.play();
    }
}

class StatusBarDto {
    private final String text;
    private final Paint paint;

    public StatusBarDto(String text, Paint paint) {
        this.text = text;
        this.paint = paint;
    }

    public String getText() {
        return text;
    }

    public Paint getPaint() {
        return paint;
    }
}