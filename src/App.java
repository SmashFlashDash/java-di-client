import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;
import data.PackageSin;
import javafx.application.Application;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.Arrays;

import static vniiem.TLMServer.bytesToHex;

public class App extends Application {
    Stage window;
    TableView<PackageSin> table;
    TextField inputPort;
    Button startButton, stoptButton, dropButton;
    Label statusBar;
    Thread listenUDP;
    Boolean runningListenUDP;
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

        // таблица
        // лейблы ошибки/пакетов
        // лейбл текст филд порт, в центре прилжения, кнопки старт стоп, сброс
        // статсу бар на эксепшен если сокет не подключился или ошибки

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
//        table.setItems(getPacages());
        table.getColumns().addAll(idColumn, dateColumn, angleSinColumn, crc16Column);
        tableList = table.getItems();

        // изменить цвет строки
//        table.setRowFactory(tv -> new TableRow<CustomItem>() {
//            @Override
//            protected void updateItem(CustomItem item, boolean empty) {
//                super.updateItem(item, empty);
//                if (item == null || item.getValue() == null)
//                    setStyle("");
//                else if (item.getValue() > 0)
//                    setStyle("-fx-background-color: #baffba;");
//                else if (item.getValue() < 0)
//                    setStyle("-fx-background-color: #ffd7d1;");
//                else
//                    setStyle("");
//            }
//        });

//        resultsTable.setRowFactory(row -> new TableRow<Result>() {
//            @Override
//            public void updateItem(Result item, boolean empty) {
//                super.updateItem(item, empty);
//                if (item == null) {
//                    setStyle("");
//                } else if (item.getResultType().equalsIgnoreCase("Error")) {
//                    this.setId("error");
//                } else {
//                    this.setId("not-error");
//                }
//            }
//        });
//        CSS file:
//        #error .text{
//            -fx-fill : red;
//        }
//
//        #not-error .text{
//            -fx-fill : black;
//        }


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
        HBox hBoxStatusBar = new HBox();
        hBoxStatusBar.setPadding(new Insets(0,0,0,10));
        hBoxStatusBar.getChildren().add(statusBar);
        hBoxStatusBar.getStyleClass().add("statusBar");
        // main panel
        VBox vBox = new VBox();
        vBox.setPadding(Insets.EMPTY);
        vBox.getChildren().addAll(table, hBox, hBoxStatusBar);
        VBox.setVgrow(table, Priority.ALWAYS);

        // transparentStage надо делать кнопки закрывания
        // scene.setFill(Color.TRANSPARENT);
        // window.initStyle(StageStyle.TRANSPARENT);

        Scene scene = new Scene(vBox);
        scene.getStylesheets().add("res/styles.css");
        window.setScene(scene);
        window.show();
    }

    // TODO: можно сделать обновление информации о пакетах в потоке

    //TODO: запустить прослшуку порта, если ексепшен ретурн, и написать в статус бар, внизу мелки лейюл во весь экран
    //  запетить изменять текст филд порт
    //  красным надпись
    //  если засинхронились кадры, создавать обьект и отправлять в таблицу, если ошибка в кадре, изменить коунтер
    //  подсвечивать строку красным
    //
    private void statusBarError(String text){
        statusBar.setText(text);
        statusBar.setStyle("-fx-text-fill:  red;");
    }

    private void statusBarInfo(String text){
        statusBar.setText(text);
        statusBar.setStyle("-fx-text-fill: rgba(250, 250, 250, 255)");
    }

    private void listenPort() {
        System.out.println("Clicked");
        Integer port = null;
        try {
            port = Integer.parseInt(inputPort.getText());
        } catch (NumberFormatException ex){
            statusBarError("Port должен быть цифрой");
            return;
        }
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            statusBarError("Port недоступен");
            return;
        }
        listenUDP = new Thread(this::_listenPort);
        listenUDP.setName("listenUDP");
        runningListenUDP = true;
        listenUDP.start();
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
                    statusBarInfo("Прием даных остановлен");
                    continue;
                } else {
                    socket.close();
                    statusBarError("Port недоступен");
                }
            }
            frameBuf.put(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()));
            frameBuf.flip();
            if (!synched) {
                for (int i = 0; i < frameBuf.limit() - 3; i++) {
                    frameBuf.position(i);
                    if (PackageSin.validateSynch(frameBuf, frameBuf.position())) {
                        synched = true;
                        System.out.println("Синронизация");
//                        System.out.println("CutBefor\t" + bytesToHex(frameBuf.array(), frameBuf.position(), frameBuf.remaining()));
                        break;
                    }
                }
            }
            if (synched && frameBuf.remaining() >= 26) {
                byte[] bytes = new byte[26];
                frameBuf.get(bytes);
//                System.out.println("Frame\t\t" + bytesToHex(bytes, 0, bytes.length));
                // TODO: убрать ексепшены, сделать поле с ошибками
                // првоерить что буфер поменялся после компакт
                // кидать поле в таблицу
                PackageSin data = new PackageSin(bytes);
                tableList.add(data);
//                table.scrollTo(tableList.size());
                if (data.isHaveErrors()){
                    synched = false;
                }
            }
            frameBuf.compact();
        }
    }

    //TODO: остановить прослушку порта
    //  разрешить изменить надпись порт
    private void stopPort() {
        System.out.println("Clicked");
        socket.close();
    }

    private void _stopPort() {
    }

    // TODO: очитить поля в таблице и сбросить лейблы ошибок и принято пакетов
    private void dropTable() {
        System.out.println("Clicked");
    }


    public ObservableList<PackageSin> getPacages(){
        ObservableList<PackageSin> packages = FXCollections.observableArrayList();
        packages.add(new PackageSin(0, LocalDateTime.now(), 11d, (short) 11));
        packages.add(new PackageSin(1, LocalDateTime.now(), 22d, (short) 11));
        packages.add(new PackageSin(1, LocalDateTime.now(), 88d, (short) 88));
        packages.add(new PackageSin(1, LocalDateTime.now(), 111d, (short) 11));
        return packages;
    }
}
