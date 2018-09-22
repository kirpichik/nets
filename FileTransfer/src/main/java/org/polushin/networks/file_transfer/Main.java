package org.polushin.networks.file_transfer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String USAGE = "Usage: -send <hostname:port> <file> or -storage <port> <storage-path>";

    public static void main(String[] args) {
        if (args.length < 3)
            exitWithError(USAGE);

        switch (args[0]) {
            case "-send":
                sendFile(args);
                return;
            case "-storage":
                filesStorage(args);
                return;
            default:
                exitWithError(USAGE);
        }
    }

    /**
     * Опция отправки файла.
     */
    private static void sendFile(String[] args) {
        int pos = args[1].indexOf(":");
        if (pos == -1)
            exitWithError(USAGE);

        int port;
        InetAddress address;
        try {
            port = Integer.parseInt(args[1].substring(pos + 1));
            address = InetAddress.getByName(args[1].substring(0, pos));
        } catch (NumberFormatException | UnknownHostException e) {
            exitWithError(USAGE);
            return;
        }

        List<File> files = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            File file = prepareFile(args[i]);
            if (file != null)
                files.add(file);
        }

        if (files.isEmpty())
            exitWithError("No files to send.");

        try (FileSender sender = new FileSender(address, port)) {
            files.forEach(sender::sendFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Проверяет валидность переданного файла.
     */
    private static File prepareFile(String path) {
        File file = new File(path);

        if (!file.exists())
            System.out.format("File \"%s\" not found.\n", path);
        else if (!file.isFile())
            System.out.format("\"%s\" is not file.\n", path);
        else if (file.length() >= Utils.MAX_FILE_SIZE)
            System.out.format("\"%s\" is to large.\n", path);
        else
            return file;

        return null;
    }

    /**
     * Опция приема файлов.
     */
    private static void filesStorage(String[] args) {
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            exitWithError(USAGE);
            return;
        }

        File storage = new File(args[2]);
        if (!storage.exists())
            exitWithError(String.format("Storage path \"%s\" not found.", args[2]));
        else if (!storage.isDirectory())
            exitWithError("Storage path must be directory.");

        StorageServer server;
        try {
            server = new StorageServer(port, storage);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        server.startServer();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
    }

    private static void exitWithError(String message) {
        System.out.println(message);
        System.exit(-1);
    }

}
