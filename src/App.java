import data.PackageSin;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

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
    Thread threadStatusBar;
    Boolean runningStatusBar = false;
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
        table.setRowFactory(tv -> new TableRow<PackageSin>() {
            @Override
            protected void updateItem(PackageSin packageSin, boolean empty) {
                super.updateItem(packageSin, empty);
                if (packageSin != null && packageSin.isHaveErrors())
                    setStyle("-fx-background-color: red;");
                else {
                    setStyle("");
                }
            }
        });
//        if (packageSin.isHaveErrors()) {
//            setStyle("-fx-background-color: #baffba;");
//        } else {
//            setStyle("");
//        }

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
        statusBar = new Label("Готово");
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
        if (runningStatusBar){
            return;
        }
        statusBar.setText(text);
        statusBar.setStyle("-fx-text-fill:  red;");
    }

    private void statusBarInfo(String text){
        if (runningStatusBar){
            threadStatusBar.interrupt();
        }
        statusBar.setText(text);
        statusBar.setStyle("");
    }
    private void statusBatInfoDisplayTime(String text, int mills){
        runningStatusBar = true;
        threadStatusBar = new Thread(()->{
            statusBar.setText(text);
            statusBar.setStyle("");
            try {
                Thread.sleep(mills);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            runningStatusBar = false;
            statusBarInfo("Готово");
        });
        threadStatusBar.start();
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
        inputPort.setDisable(true);
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            statusBarError("Port недоступен");
            return;
        }
        threadListenUDP = new Thread(this::_listenPort);
        threadListenUDP.setName("listenUDP");
        runningListenUDP = true;
        threadListenUDP.start();
        statusBarInfo("Прием пакетов");
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

    private void stopPort() {
        if (runningListenUDP){
            statusBatInfoDisplayTime("Остановка приема", 1000);
            runningListenUDP = false;
            try {
                threadListenUDP.join();
            } catch (InterruptedException e){
                e.printStackTrace();
            }
            socket.close();
            inputPort.setDisable(false);
        }
    }

    // TODO: очитить поля в таблице и сбросить лейблы ошибок и принято пакетов
    private void dropTable() {
        System.out.println("Clicked");
        statusBatInfoDisplayTime("Очистка таблицы", 1000);
        tableList.clear();

    }



}
