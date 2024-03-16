import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class WorkerThread extends Thread {
    // connection constants
    private static final int BUFFER_SIZE = 4096;
    private static final int EOF = -1;

    // response constants
    private static final int REQUEST_TIMEOUT_CODE = 408;
    private static final String REQUEST_TIMEOUT_PHRASE = "Request Timeout";
    private static final String HTTP_VERSION = "HTTP/1.1";

    // connection variables
    private String serverName;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private int timeout;

    public WorkerThread(String serverName, Socket socket, int timeout) {
        this.serverName = serverName;
        this.socket = socket;
        this.timeout = timeout;

        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            /*
            * The numBytes tells us how many bytes to actually read from the stream; this may
            * be different from the buffer size (ie. if the number of bytes remaining is <
            * buffer.length). This is why we cannot specify buffer.length as the number of bytes being read,
            * as we would get an IndexOutOfBounds exception when we reach the end.
            */
            socket.setSoTimeout(timeout);
            int numBytes = 0;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((numBytes = inputStream.read(buffer)) != EOF) {
                
            }

            



                // int numBytes = 0;
                // byte[] buffer = new byte[StreamClient.this.BUFFER_SIZE];

                // try {
                //     while () {
                //         System.out.println("R " + numBytes);
                //         fileOutputStream.write(buffer, OFFSET, numBytes);
                //     }
                //     /*
                //      * we do not need the bytes to be written to the file as we are reading it; this is because the concurrency
                //      * and interaction between client and server is related to them sending the file to each other. The server
                //      * already flushes (as specified by Dr. Ghaderi), and we have flushed on the client side when writing. There is no
                //      * urgency when outputting to file.
                //      * 
                //      * We can comfortably flush at the end.
                //      */
                //     fileOutputStream.flush();
                // } 
                // catch (IOException e) {
                //     e.printStackTrace();
                // }

        } 
        catch (SocketTimeoutException e) {
            e.printStackTrace();
            timeoutResponse();
        }
        catch (SocketException e) {
            e.printStackTrace();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void timeoutResponse() {

    }

    /**
     * Create a properly formatted HTTP GET response message.
     *
     * @return A response message that is ready to send to the client.
     */
    private String constructGetResponse(int httpStatusCode, String httpStatusPhrase, boolean isOK) {
        String httpVersion = HTTP_VERSION;

        if (isOK) {

        } else {
            String dateHeader = "Date: " + ServerUtils.getCurrentDate() + "\r\n";
            String serverHeader = "Server: " + serverName + "\r\n";
            String connectionHeader = "Connection: close\r\n";
        }

        String hostHeader = "Host: " + hostname + "\r\n";
        String connectionHeader = "Connection: close\r\n";

        /*
         * A request message has these three "components"; this is why the code is broken up
         * in a similar manner, but these could just as easily be combined into a single string.
         */
        String requestLine = String.format("%s /%s %s\r\n", httpMethod, pathname, httpVersion);
        String headerLines = hostHeader + connectionHeader;
        String endOfHeaderLines = "\r\n";
        String request = requestLine + headerLines + endOfHeaderLines;
        
        return request;
    }
}
