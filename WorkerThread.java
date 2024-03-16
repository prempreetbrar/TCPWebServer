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
    private static final String EOL = "\r\n";
    private static final String END_OF_HEADERS = EOL;

    // connection variables
    private String serverName;
    private String root;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private int timeout;

    public WorkerThread(String serverName, String root, Socket socket, int timeout) {
        this.serverName = serverName;
        this.root = root;
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
     * Create a properly formatted HTTP response message.
     *
     * @return A response message that is ready to send to the client.
     */
    private String constructResponse(int httpStatusCode, String httpStatusPhrase, boolean isOK) {
        String statusLine = HTTP_VERSION + httpStatusCode + httpStatusPhrase;
        String headers = constructHeaders(isOK);

        /*
         * A response message has four "components"; this is why the code is broken up
         * in a similar manner, but these could just as easily be constructed as a single string.
         */
        String response = statusLine + headers + END_OF_HEADERS;
        return response;
    }

    private String constructHeaders(boolean isOK) {
        String date = "Date: " + ServerUtils.getCurrentDate() + EOL;
        String server = "Server: " + serverName + EOL;
        String connection = "Connection: close" + EOL;

        if (isOK) {
            // String lastModified = "Last-Modified: " + ServerUtils.getLastModified() + EOL;
            // String contentLength = "Content-Length: " + ServerUtils.getContentLength() + EOL;
            // String contentType = "Content-Type: " + ServerUtils.getContentType() + EOL;
            // return date + server + lastModified + contentLength + contentType + connection;
        } 
        return date + server + connection; 
    }
}
