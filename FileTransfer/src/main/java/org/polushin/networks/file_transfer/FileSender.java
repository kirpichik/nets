package org.polushin.networks.file_transfer;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Отправитель файлов на сервер-хранилище.
 */
public class FileSender {

    private static final int BUFFER_SIZE = 1024;

    private final Socket socket;
    private final BlockingQueue<File> sendQueue = new LinkedBlockingQueue<>();
    private final Thread sendThread;

    private volatile boolean running = true;

    public FileSender(InetAddress host, int port) throws IOException {
        socket = new Socket(host, port);
        sendThread = new Thread(() -> {
            try {
                run();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        });
        sendThread.start();
    }

    /**
     * Добавляет файл в очередь на отправку.
     *
     * @param file Файл.
     *
     * @throws IllegalStateException Если соединение закрыто.
     */
    public void sendFile(File file) throws IllegalStateException {
        if (!running || socket.isClosed()) {
            if (socket.isClosed())
                close();
            throw new IllegalStateException("Connection closed.");
        }

        try {
            sendQueue.put(file);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Завершает отправку всех файлов и закрывает соеднинение.
     */
    public void close() {
        if (!running)
            return;
        running = false;
        sendThread.interrupt();
        try {
            sendThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void run() throws IOException {
        while (running) {
            try {
                sendFileImmediately(sendQueue.take());
            } catch (InterruptedException ignored) {
            }
        }

        File file;
        while ((file = sendQueue.poll()) != null)
            sendFileImmediately(file);
        socket.close();
    }

    /**
     * Выполняет отправку файла.
     */
    private void sendFileImmediately(File file) throws IOException {
        OutputStream output = socket.getOutputStream();

        // Отправляем размер файла
        output.write(ByteBuffer.allocate(8).putLong(file.length()).array(), 3, 5);
        // Отправляем длину имени
        output.write(ByteBuffer.allocate(4).putInt(file.getName().length()).array(), 2, 2);
        // Отправляем имя файла
        output.write(file.getName().getBytes(StorageServer.CHARSET));

        // Отправляем файл
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = input.read(buffer)) > 0)
                output.write(buffer, 0, len);
        }
    }
}
