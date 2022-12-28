package vniiem;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JFrame;

import net.miginfocom.swing.MigLayout;

public class TLMClient extends JFrame{
	private static final long serialVersionUID = -8319698778495311052L;
	private static final int UDP_PORT = 15000;
	private static final int TLM_OBJECT_SIZE = 26;
	
	private DatagramSocket socket;
    private InetAddress address;

    private Timer fillTimer, sendTimer;
    private ConcurrentLinkedQueue<Byte> queue = new ConcurrentLinkedQueue<Byte>();
    
    private static Object syncObject = new Object();
    
    private AtomicInteger counter;
    private Random rand;
    
    public TLMClient() {
    	initialize();
	}
    
    private void initialize()
    {
    	setTitle("TLM");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		setBounds(0, 0, 250, 100);
		setResizable(true);
		getContentPane().setLayout(new MigLayout("", "[grow, fill]", "[grow, fill]"));
		
		JButton start_bt = new JButton("Start");
		start_bt.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				start_bt.setEnabled(false);
				try {
					init();
				} catch (SocketException ex) {
					ex.printStackTrace();
					start_bt.setEnabled(true);
					close();
				}
			}
		});
		getContentPane().add(start_bt, "cell 0 0");
		
		JButton stop_bt = new JButton("Stop");
		stop_bt.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				start_bt.setEnabled(true);
				close();
			}
		});
		getContentPane().add(stop_bt, "cell 0 0");
		
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				close();
				System.exit(0);
			}
		});
    }
    
    private void init() throws SocketException
    {
    	socket = new DatagramSocket();
    	try {
			address = InetAddress.getByName("localhost");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
    	
    	counter = new AtomicInteger(0);
    	rand = new Random();
    	 
		fillTimer = new Timer("fillTimer", true);
		fillTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				fillTLM();
			}
		}, 50, 200);
		
		sendTimer = new Timer("sendTimer", true);
		sendTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				sendTLM();
			}
		}, 200, 200);
    }

    private void fillTLM()
    {
    	ByteBuffer bb = ByteBuffer.allocate(TLM_OBJECT_SIZE);
    	bb.order(ByteOrder.LITTLE_ENDIAN);
    	bb.putInt(0x12345678);
    	
    	if(rand.nextInt(10) == 0)
    	{
    		bb.putInt(0x57849563);
    	}
    	else
    	{
    		bb.putInt(counter.incrementAndGet());
    	}
    	
		bb.putDouble(TimeManipulation.getUnixTimeUTC(LocalDateTime.now()));
		
		bb.putDouble(Math.sin(rand.nextInt(10)));
		
		if(rand.nextInt(10) == 0)
		{
			bb.putShort((short) 0x1795);
		}
		else
		{
			bb.putShort(CRC16_CCITT.crc16Ccitt(bb, 0, bb.position()));
		}
		
		synchronized (syncObject) {
			byte[] buff = bb.array();
			for (int i = 0; i < buff.length; i++) {
				queue.offer(buff[i]);
			}
		}
    }
    
	private void sendTLM() 
	{
		byte[] buff = new byte[TLM_OBJECT_SIZE - 1];
		synchronized (syncObject) {
			if(queue.size() >= buff.length)
			{
				for (int i = 0; i < buff.length; i++) {
					buff[i] = queue.poll();
				}
			}
		}
		
		DatagramPacket packet = new DatagramPacket(buff, buff.length, address, UDP_PORT);
		try {
			socket.send(packet);
		} catch (IOException e) {
			if (socket.isClosed()) {
				sendTimer.cancel();
			} else {
				e.printStackTrace();
				socket.close();
			}
		}
	}

	private void close() {
		if(socket != null)
		{
			socket.close();
			fillTimer.cancel();
			sendTimer.cancel();
		}
    }
	
	public static void main(String[] args) {
		TLMClient client = new TLMClient();
		client.setLocationRelativeTo(null);
		client.setVisible(true);
	}
}
