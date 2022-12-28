package vniiem;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class TLMServer {
	private DatagramSocket socket;
    private boolean running;
    private byte[] buf = new byte[25];
    private Thread runnerThread;
    
    public void init(int port) throws SocketException
    {
    	socket = new DatagramSocket(port);
    	
		runnerThread = new Thread(this::listenLoopUDP);
		runnerThread.setName("listenLoopUDP");
		
		running = true;
		runnerThread.start();
    }
	
	public void listenLoopUDP() {
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
			System.out.println(bytesToHex(packet.getData(), 0, packet.getData().length));
		}
	}
    
	private static final char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes,int offset,int len) {
		if (bytes==null)
			return null;
		char[] hexChars = new char[len * 2];
		for ( int j = offset; j < offset+len; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[(j-offset) * 2] = hexArray[v >>> 4];
			hexChars[(j-offset) * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	
	public void close() {
		if(socket != null)
		{
			socket.close();
			try {
				runnerThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		TLMServer server = new TLMServer();
		try {
			server.init(15000);
		} catch (SocketException ex) {
			ex.printStackTrace();
			server.close();
		}
	}
}
