package org.polushin.networks.file_transfer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сервер-хранилище файлов.
 */
public class StorageServer {

    private final File storage;
    private final ServerSocket serverSocket;
    private final ExecutorService threadpool;
    private final Thread accepterThread;

    private volatile boolean running = true;

    public StorageServer(int port, File storage) throws IOException {
        this.storage = storage;
        serverSocket = new ServerSocket(port);
        threadpool = Executors.newCachedThreadPool();
        accepterThread = new Thread(this::run);
    }

    /**
     * Запускает обработку входящих соединений.
     */
    public void startServer() {
        if (running)
            return;
        accepterThread.start();
    }

    /**
     * Останавливает обработку входящих соединений.
     */
    public void stopServer() {
        if (!running)
            return;
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadpool.shutdown();
    }

    private void run() {
        while (running) {
            try {
                threadpool.submit(new ClientHandler(serverSocket.accept(), storage));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
