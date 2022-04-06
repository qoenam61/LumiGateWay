package com.example.channel;

import android.util.Log;

import java.io.IOException;
import java.net.*;

public class DirectChannel {
    private static final String TAG = "DirectChannel";

    private static final int SOCKET_TIMEOUT = 60000; // milliseconds

    private String ip;
    private int port;
    private DatagramSocket socket;

    public DirectChannel(String destnationIp, int destinationPort) throws IOException {
        Log.d(TAG, "DirectChannel ip : " + destnationIp + " port : " + destinationPort);
        this.ip = destnationIp;
        this.port = destinationPort;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(SOCKET_TIMEOUT);
    }

    public byte[] receive() throws IOException {
        byte buffer[] = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        int responseLength = packet.getLength();
        byte response[] = new byte[responseLength];
        System.arraycopy(buffer, 0, response, 0, responseLength);
        return response;
    }

    public void send(byte[] bytes) throws IOException {
        InetSocketAddress address = new InetSocketAddress(ip, port);
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address);
        socket.send(packet);
    }
}
