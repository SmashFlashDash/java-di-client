package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class App extends Application {
    Stage window;
    TableView<PackageSin> table;
    TextField inputPort;
    Button startButton, stoptButton, dropButton;
    Label statusBar;
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
        statusBar = new Label("statusBar");
        statusBarSetText("Готово", "");
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
            System.out.println("Whataaaa");
            if (runningListenUDP){
                stopPort();
            }
        });
        window.setScene(scene);
        window.show();
    }

    private void statusBarSetText(String text, String codeColor){
            statusBar.setText(text);
            if (codeColor.equals("err")){
                statusBar.setStyle("-fx-text-fill: red");
            } else {
                statusBar.setStyle("");
            }
    }

    private void listenPort() {
        int port;
        try {
            port = Integer.parseInt(inputPort.getText());
        } catch (NumberFormatException ex){
            statusBarSetText("Порт должен быть цифрой", "err");
            return;
        }
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            statusBarSetText("Порт занят", "err");
            return;
        }
        threadListenUDP = new Thread(this::_listenPort);
        threadListenUDP.setName("listenUDP");
        threadListenUDP.setDaemon(true);
        runningListenUDP = true;
        threadListenUDP.start();
        statusBarSetText("Прием данных", "");
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
        statusBarSetText("Прием данных остановлен", "");
    }

    private void dropTable() {
        tableList.clear();
        statusBarSetText("Таблица очищена", "");
        if (runningListenUDP){
            statusBarSetText("Прием данных", "");
        }
    }
}
