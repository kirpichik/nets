package org.polushin.networks.file_transfer;

import java.nio.charset.Charset;

/**
 * Сборник общих констант и методов.
 */
public class Utils {

    // 1 Терабайт в байтах
    public static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;

    // Стандартная кодировка для имени файла
    public static final Charset CHARSET = Charset.forName("UTF-8");

    // Длина типа long в байтах
    public static final int LONG_IN_BYTES_SIZE = 8;
    // Длина типа int в байтах
    public static final int INT_IN_BYTES_SIZE = 4;

    // Длина числа размера файла в байтах
    public static final int FILE_LEN_SIZE = 5;
    // Длина числа длины имени файла в байтах
    public static final int FILENAME_LEN_SIZE = 2;

    /**
     * Коды ответы сервера являются ordinal-ами констант.
     */
    public enum ServerResponses {
        UPLOAD_APPROVED,
        NOT_ENOUGH_FREE_SPACE,
        FILE_SAVED,
        UNKNOWN_ERROR
    }
}
