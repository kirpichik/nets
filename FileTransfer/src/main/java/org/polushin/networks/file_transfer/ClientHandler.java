package org.polushin.networks.file_transfer;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

import static org.polushin.networks.file_transfer.Utils.*;

/**
 * Обработчик входящих запросов от клиентов.
 */
public class ClientHandler implements Runnable {

    // Размер буфера для записи имени файла в UTF-8 (4096 символов, до 4-х байт каждый)
    private static final int FILENAME_BUFFER_SIZE = 4096 * 4;
    private static final int BUFFER_SIZE = 1024;

    private final File storage;
    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    // Буферы предварительно выделены во избежание излишних аллокаций памяти
    private final ByteBuffer sizesBuffer = ByteBuffer.wrap(new byte[LONG_IN_BYTES_SIZE]);
    private final byte[] filenameBuffer = new byte[FILENAME_BUFFER_SIZE];
    private final byte[] buffer = new byte[BUFFER_SIZE];

    public ClientHandler(Socket socket, File storage) throws IOException {
        this.storage = storage;
        this.socket = socket;
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    @Override
    public void run() {
        try {
            System.out.println("Connected " + socket.getInetAddress());
            handleConnection(socket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает входящее подключение.
     */
    private void handleConnection(Socket socket) throws IOException {
        while (true) {
            long fileSize = readFileSize();
            // Если размер файла равен 0, передачу следует завершить
            if (fileSize == 0) {
                socket.close();
                return;
            }

            // Проверяем свободное место
            if (storage.getFreeSpace() <= fileSize) {
                outputStream.write(ServerResponses.NOT_ENOUGH_FREE_SPACE.ordinal());
                continue;
            }

            // Подтверждаем отправку
            outputStream.write(ServerResponses.UPLOAD_APPROVED.ordinal());

            // Сохраняем файл
            try {
                uploadFile(prepareFile(readFilename(readFilenameSize())), fileSize);
            } catch (IOException e) {
                outputStream.write(ServerResponses.UNKNOWN_ERROR.ordinal());
                socket.close();
                throw e;
            }

            // Подтверждаем получение
            outputStream.write(ServerResponses.FILE_SAVED.ordinal());
        }
    }

    /**
     * Загружает и сохраняет файл.
     *
     * @param file Место сохранения файла.
     * @param fileSize Размер файла.
     */
    private void uploadFile(File file, long fileSize) throws IOException {
        System.out.println(String.format("Uploading \"%s\" from %s...", file.getName(), socket.getInetAddress()));

        OutputStream fileOutput = new FileOutputStream(file);
        long fullLen = 0;
        int len;
        do {
            len = inputStream.read(buffer, 0, buffer.length);
            fullLen += len;
            fileOutput.write(buffer, 0, len);
        } while (fullLen < fileSize);
        fileOutput.close();

        System.out.println(String.format("File \"%s\" stored.", file.getName()));
    }

    /**
     * Подготавливает файл для записи (выбирает свободное имя файла).
     *
     * @return Файл.
     */
    private File prepareFile(String filename) {
        File file = new File(storage, filename);
        if (!file.exists())
            return file;

        int pos = filename.lastIndexOf('.');
        String prefix, suffix;
        if (pos == -1) {
            prefix = filename;
            suffix = "";
        } else {
            prefix = filename.substring(0, pos);
            suffix = filename.substring(pos);
        }

        int number = 1;
        do {
            file = new File(storage, String.format("%s (%d)%s", prefix, number++, suffix));
        } while (file.exists());

        return file;
    }

    /**
     * Считывает имя файла.
     *
     * @param size Длина в байтах имени файла.
     *
     * @return Имя файла.
     */
    private String readFilename(int size) throws IOException {
        System.out.println("Filename size: " + size);
        readAtLeastBytes(filenameBuffer, 0, size);
        return new String(filenameBuffer, 0, size);
    }

    /**
     * @return Считаная длина имени файла.
     */
    private int readFilenameSize() throws IOException {
        clearSizesBuffer();
        readAtLeastBytes(sizesBuffer.array(), LONG_IN_BYTES_SIZE - FILENAME_LEN_SIZE, FILENAME_LEN_SIZE);
        return sizesBuffer.getInt(INT_IN_BYTES_SIZE);
    }

    /**
     * @return Считаный размер файла.
     */
    private long readFileSize() throws IOException {
        clearSizesBuffer();
        readAtLeastBytes(sizesBuffer.array(), LONG_IN_BYTES_SIZE - FILE_LEN_SIZE, FILE_LEN_SIZE);
        return sizesBuffer.getLong();
    }

    /**
     * Очищает буфер размеров и заполняет его нулями.
     */
    private void clearSizesBuffer() {
        sizesBuffer.putLong(0, 0).clear();
    }

    /**
     * Считывает требуемое количество байт в массив.
     *
     * @param buffer Буфер для сохранения данных.
     * @param offset Смещение от начала буфера.
     * @param required Минимальное количество байт для возврата.
     */
    private void readAtLeastBytes(byte[] buffer, int offset, int required) throws IOException {
        int len = 0;
        while (len < required)
            len += inputStream.read(buffer, offset + len, required - len);
    }
}