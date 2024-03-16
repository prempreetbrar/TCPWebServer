/**
 * Utils Class
 * 
 * CPSC 441 - L01 - T01
 * Assignment 3
 * 
 * TA: Amir Shani
 * Student: Prempreet Brar
 * UCID: 30112576
 * 
 * A class containing constants and methods used by both the server and workers. 
 */

import java.io.Closeable;
import java.io.IOException;

public class Utils {
    // numbers follow standard Java convention
    public static final int SUCCESSFUL_TERMINATION = 0;
    public static final int UNSUCCESSFUL_TERMINATION = -1;

    // I/O constants
    public static final int BUFFER_SIZE = 4096;
    public static final int EOF = -1;
    public static final int NO_BYTE = -1;
    public static final int OFFSET = 0;

    // request constants
    public static final String DEFAULT_PATH = "/";
    public static final String DEFAULT_LOCATION = "/index.html";

    // response constants
    public static final String STRING_TO_BYTE_CHARSET = "US-ASCII";

    public static final int TIMEOUT_CODE = 408;
    public static final String TIMEOUT_PHRASE = "Request Timeout";
    public static final int BAD_CODE = 400;
    public static final String BAD_PHRASE = "Bad Request";
    public static final int NOT_FOUND_CODE = 404;
    public static final String NOT_FOUND_PHRASE = "Not Found";
    public static final int OK_CODE = 200;
    public static final String OK_PHRASE = "OK";

    public static final String HTTP_VERSION = "HTTP/1.1";
    public static final String HTTP_METHOD = "GET";
    public static final String EOL = "\r\n";
    public static final String END_OF_HEADERS = EOL;

    /**
     * Close all opened streams, sockets, and other resources before terminating the program.
     *
     * @param resources all resources which need to be closed
     */
    public static void closeGracefully(Closeable... resources) {
        /*
         * We need to surround this with a try-catch block because the closing itself can raise
         * an IOException. In this case, if closing fails, there is nothing else we can do. We must also
         * ensure the resource is not null. This is because other parts of the program instantiate certain
         * resources to null before reassignment.
         */
        try {
            for (Closeable resource : resources) {
                if (resource != null) {
                    resource.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Join all opened threads (wait for them to finish executing).
     *
     * @param threads all threads which need to finish execution.
     */
    public static void joinGracefully(Thread... threads) {
        /*
         * We need to surround this with a try-catch block because the joining itself can raise
         * an InterruptedException. In this case, if joining fails, there is nothing else we can do. We must also
         * ensure that the thread is not null. This is because other parts of the program instantiate a Thread
         * variable to null before reassignment.
         */
        try {
            for (Thread thread : threads) {
                if (thread != null) {
                    thread.join();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
