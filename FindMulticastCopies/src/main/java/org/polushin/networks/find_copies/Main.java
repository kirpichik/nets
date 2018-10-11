package org.polushin.networks.find_copies;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final String IDENTIFY_MESSAGE = "Hello from me to me!";
    private static final Charset CHARSET = Charset.forName("UTF-8");
    private static final int PORT = 17120;

    private static final long MULTICAST_DELAY = 10 * 1000;
    private static final long ALIVE_TIMEOUT = 20 * 1000;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: <multicast-IPv4 or multicast-IPv6 address>");
            System.exit(1);
        }

        InetAddress group = null;
        try {
            InetAddress group = InetAddress.getByName(args[0]);
        } catch(UnknownHostException e) {
            System.err.format("Address: %s is incorrect.\n", args[1]);
        }
        final MulticastSocket serverSocket = new MulticastSocket(PORT);
        final DatagramSocket clientSocket = new DatagramSocket();
        final DatagramPacket receivePacket = new DatagramPacket(new byte[IDENTIFY_MESSAGE.length()],
                                                                IDENTIFY_MESSAGE.length());
        final DatagramPacket sendPacket = new DatagramPacket(IDENTIFY_MESSAGE.getBytes(CHARSET),
                                                             IDENTIFY_MESSAGE.length(), group, PORT);

        serverSocket.joinGroup(group);

        final Map<InetAddress, Long> knownCopies = new HashMap<>();
        long nextMulticastTime = 0;

        //noinspection InfiniteLoopStatement
        while (true) {
            if (nextMulticastTime < System.currentTimeMillis()) {
                nextMulticastTime = System.currentTimeMillis() + MULTICAST_DELAY;
                clientSocket.send(sendPacket);
            }

            knownCopies.entrySet().removeIf(entry -> {
                if (entry.getValue() < System.currentTimeMillis()) {
                    System.out.println("Lost: " + entry.getKey());
                    return true;
                }
                return false;
            });

            int timeout = (int) (nextMulticastTime - System.currentTimeMillis());
            if (timeout <= 0)
                timeout = 1; // Нулевой таймаут интерпретируется как вечный
            serverSocket.setSoTimeout(timeout);

            try {
                serverSocket.receive(receivePacket);
            } catch (SocketTimeoutException e) {
                continue;
            }

            if (new String(receivePacket.getData(), CHARSET).equals(IDENTIFY_MESSAGE))
                if (knownCopies.put(receivePacket.getAddress(), System.currentTimeMillis() + ALIVE_TIMEOUT) == null)
                    System.out.println("New copy: " + receivePacket.getAddress());

        }
    }
}
