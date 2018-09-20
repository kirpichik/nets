package org.polushin.networks.file_transfer;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {

    private static final String USAGE = "Usage: -send <hostname:port> <file> or -storage <port> <storage-path>";
    // 1 Терабайт в байтах
    private static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;

    public static void main(String[] args) {
        if (args.length != 3)
            exitWithError(USAGE);

        switch (args[0]) {
            case "-send":
                sendFile(args);
                return;
            case "-storage":
                filesStorage(args);
                return;
            default:
                System.out.println(USAGE);
                System.exit(-1);
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
        }

        File file = new File(args[2]);
        if (!file.exists())
            exitWithError(String.format("File \"%s\" not found.", args[2]));
        else if (!file.isFile())
            exitWithError("Only files can be sent.");
        else if (file.length() > MAX_FILE_SIZE)
            exitWithError("To large file.");

        // TODO
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
        }

        File storage = new File(args[2]);
        if (!storage.exists())
            exitWithError(String.format("Storage path \"%s\" not found.", args[2]));
        else if (!storage.isDirectory())
            exitWithError("Storage path must be directory.");

        // TODO
    }

    private static void exitWithError(String message) {
        System.out.println(message);
        System.exit(-1);
    }

}
