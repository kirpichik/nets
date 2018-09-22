package org.polushin.networks.file_transfer;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.polushin.networks.file_transfer.Utils.*;

/**
 * Отправитель файлов на сервер-хранилище.
 */
public class FileSender {

    private static final int BUFFER_SIZE = 1024;
    private static final byte[] ZERO_FILE_LEN = new byte[5];

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final BlockingQueue<File> sendQueue = new LinkedBlockingQueue<>();
    private final Thread sendThread;

    private volatile boolean running = true;

    public FileSender(InetAddress host, int port) throws IOException {
        socket = new Socket(host, port);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();

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

        // Отправка длины файла равной нулю означает завершение передачи.
        socket.getOutputStream().write(ZERO_FILE_LEN);

        socket.close();
    }

    /**
     * Выполняет отправку файла.
     */
    private void sendFileImmediately(File file) throws IOException {
        // Отправляем размер файла
        outputStream.write(ByteBuffer.allocate(LONG_IN_BYTES_SIZE).putLong(file.length()).array(),
                           LONG_IN_BYTES_SIZE - FILE_LEN_SIZE, FILE_LEN_SIZE);

        // Получаем ответ и проверяем подтверждение от сервера
        int response = inputStream.read();
        if (response == ServerResponses.NOT_ENOUGH_FREE_SPACE.ordinal()) {
            System.out.println(String.format("Server has no free space to store \"%s\" file.", file.getAbsolutePath()));
            return;
        } else if (response != ServerResponses.UPLOAD_APPROVED.ordinal()) {
            System.out.println(String.format("An unknown server error occurred while trying to upload \"%s\" file.",
                                             file.getAbsolutePath()));
            return;
        }

        // Подготавливаем массив байт имени
        // При ограничении длины имени файла в 2^12 символов в кодировке UTF-8 это займет максимум 2^14 байт
        // Так как UTF-8 компанует символы используя от 1 до 4 байт.
        byte[] nameInBytes = file.getName().getBytes(CHARSET);
        // Отправляем длину имени
        outputStream.write(ByteBuffer.allocate(INT_IN_BYTES_SIZE).putInt(nameInBytes.length).array(),
                           INT_IN_BYTES_SIZE - FILENAME_LEN_SIZE, FILENAME_LEN_SIZE);
        // Отправляем имя файла
        outputStream.write(nameInBytes);

        // Отправляем файл
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = input.read(buffer)) > 0)
                outputStream.write(buffer, 0, len);
        }

        response = inputStream.read();
        if (response == ServerResponses.FILE_SAVED.ordinal())
            System.out.println(String.format("Cannot upload file: \"%s\"", file.getAbsolutePath()));
        else
            System.out.println(String.format("File \"%s\" uploaded.", file.getAbsolutePath()));
    }
}
