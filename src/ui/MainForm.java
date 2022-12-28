package ui;

import data.PackageSin;

import javax.swing.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Timer;

import static vniiem.TLMServer.bytesToHex;

public class MainForm {
    public static void MainForm(String[] args) {
        // запстить TLMClient-Server.mian запускают выдачу данных?
        // там используется TimeManipulattion в Client
        // CRC16_CCITT тоже в клиент fillTLM

        // TLMC CLient создает данынt и gui наследутеся от JFrame
        // в конструкторе вызывает initilize

//        // app
//        JFrame frame = new JFrame();
//        // основное окно
//        MainForm textEditor = new MainForm();
//        frame.setContentPane(textEditor);
//
////        frame.setTitle("MegaApp");
////        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
////        frame.setLocationRelativeTo(null);
////        frame.pack();
////        frame.setMinimumSize(frame.getSize());
//        frame.setVisible(true);
        App app = new App(15000);
        app.setLocationRelativeTo(null);
        app.setVisible(true);
    }
}

class App extends JFrame{
    private InetAddress address;
    private Timer fillTimer, sendTimer;
    private final byte[] buf = new byte[256];
    private final ByteBuffer frameBuf = ByteBuffer.allocate(26*2).order(ByteOrder.LITTLE_ENDIAN);
    byte[] sycnho = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x12345678).array();

    private DatagramSocket socket;
    private boolean running;
    private Thread runnerThread;

    public App(Integer port){
        setTitle("Get TLM");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setBounds(0, 0, 500, 500);
        setResizable(true);
        // отделить сервер от гуи в класс
        // заменит буфвер на двустороннюю очередь, клас в нее пакеты из сокета

//        ByteBuff?ln(Integer.toHexString(allocate.get()));

        // TODO: сделать сокет прослушивающий udp или это TLM Server
        // TODO нарисовать окно которое рисует телеметрию
        //  кол-во кадрой // кол-во байт не из пакетов

//        getContentPane().setLayout(new MigLayout("", "[grow, fill]", "[grow, fill]"));
//        JButton start_bt = new JButton("Start");
//        getContentPane().add(start_bt, "cell 0 0");

        try {
            listenSocket(port);
        } catch (SocketException e) {
            // TODO: вызват ьметод close App
            //  либо сделать в отдельном методе запустить прослушку по кнопке и забиндится на порт
            e.printStackTrace();
            System.exit(0);
        }

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                close();
                System.exit(0);
            }
        });
    }

    public void listenSocket(int port) throws SocketException {
        socket = new DatagramSocket(port);
        runnerThread = new Thread(this::listenLoopUDP);
        runnerThread.setName("listenLoopUDP");
        running = true;
        runnerThread.start();
    }

    public void listenLoopUDP() {
        boolean synched = false;
        while (running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (socket.isClosed()) {
                    running = false;
                    continue;
                } else {
                    e.printStackTrace();
                    socket.close();
                }
            }
            // TODO: првоерить подставив байт
//            frameBuf.put(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()));
//            byte[] s = {(byte) 120, (byte) 120, (byte) 120, (byte) 120, (byte) 86, (byte) 52, (byte) 18, (byte) 11,(byte) 11,(byte) 11,
//                    (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11,(byte) 11,(byte) 11,
//                    (byte) 11,(byte) 11,(byte) 11,(byte) 11,(byte) 11};
//            byte[] s = {(byte) 120, (byte) 86, (byte) 52, (byte) 18, (byte) 11, (byte) 11, (byte) 11, (byte) 11,(byte) 11,(byte) 11,
//                    (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11,(byte) 11,(byte) 11,
//                    (byte) 11,(byte) 11,(byte) 11,(byte) 11,(byte) 11};
//            byte[] s = {(byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00,(byte) 00,(byte) 00,
//                    (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00, (byte) 00,(byte) 00,(byte) 00,
//                    (byte) 00,(byte) 00,(byte) 120,(byte) 120,(byte) 86};
//            byte[] s = {(byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11,(byte) 11,(byte) 11,
//                    (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11, (byte) 11,(byte) 11,(byte) 11,
//                    (byte) 11,(byte) 120,(byte) 86,(byte) 52,(byte) 18};
//            Arrays.copyOfRange(packet.getData(), 0, packet.getLength());
            frameBuf.put(Arrays.copyOfRange(packet.getData(), 0, packet.getLength()));
            frameBuf.flip();    // чтение буфера
            if (!synched) {
                byte[] refSyncho = new byte[4];
                for (int i = 0; i < frameBuf.limit() - refSyncho.length + 1; i++) {
                    // TODO: если байт попал в конец, то он затрется, мб сделать флипы
                    frameBuf.get(refSyncho, 0, refSyncho.length);
                    frameBuf.position(frameBuf.position() - refSyncho.length + 1);
                    if (Arrays.equals(refSyncho, sycnho)) {
                        System.out.println("Синронизация");
                        // поставить mark на позицию чтения и clear
                        frameBuf.position(frameBuf.position() - 1);
//                        frameBuf.compact(); // перемещает все после позиции в начало
//                        frameBuf.flip();     // обнуляет после позиции и лимит равен позиции
//                        frameBuf.position(frameBuf.limit());
                        synched = true;
                        break;
                    }
                }
            }
            if (synched && frameBuf.remaining() >= 26) {
                // TODO: взять с Buffera кадр и потоком отправить в обработку которая шлутся в гуи
                // если начинается с кадра и fra,ebuf.Limit >= 26
                // мы читаем до минус 4 байт и если не synced оставляем 4 байта их не стирать

                byte[] bytes = new byte[26];
                frameBuf.get(bytes);
                System.out.println(bytesToHex(bytes, 0, bytes.length));
                PackageSin w = null;
                try {
                    w = new PackageSin(bytes);
                } catch (Exception e) {
                    synched = false;
                    e.printStackTrace();
                }
                if (w != null){
                    System.out.println(w);
                }
                // еще в режиме чтения

                // если класс синхронизировался
                // то буфер начинается с масик и лимит в буфере равен размеру кадра
                // создать класс разбирающий телеметрию
            }
            frameBuf.compact(); // переключаемся на запись




            // TODO после put можно в потоке проверять буфер но тогда нужен ThreadSafe
            //  искать маску кадра если нет найти первый символ и оставить его в буфере
            //  потом докинем буфер и возмем кадр, для этого можн осделать буфер в два раза больше ожидаемого
            //  если буфер переполнится вылетит нулем
            //  сделать поток который читает буфер потом обрезает с начала индекса но тогда должен лочится когда вставляем

            // UDP все равно потом выдаста данные можн покачт не думать про threadSafe
            // если измениться основной массив байт то и ByteBuffer должен измениться
        }
    }

    private void close() {
        if(socket != null)
        {
            socket.close();
            fillTimer.cancel();
            sendTimer.cancel();
            try {
                runnerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
