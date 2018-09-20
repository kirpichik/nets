package org.polushin.networks.file_transfer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;

/**
 * Сервер-хранилище файлов.
 */
public class StorageServer {

    // 1 Терабайт в байтах
    public static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;

    // Стандартная кодировка для имени файла
    public static final Charset CHARSET = Charset.forName("UTF-8");

    private final File storage;
    private final ServerSocket socket;

    public StorageServer(int port, File storage) throws IOException {
        this.storage = storage;
        socket = new ServerSocket(port);
    }

    /**
     * Запускает обработку входящих соединений.
     */
    public void startServer() {
        // TODO
    }

    /**
     * Останавливает обработку входящих соединений.
     */
    public void stopServer() {
        // TODO
    }

}
