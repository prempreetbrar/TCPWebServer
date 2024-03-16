import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class WorkerThread extends Thread {
    // numbers follow standard Java convention
    private static final int SUCCESSFUL_TERMINATION = 0;
    private static final int UNSUCCESSFUL_TERMINATION = -1;

    // connection constants
    private static final int BUFFER_SIZE = 4096;
    private static final int EOF = -1;
    private static final int NO_BYTE = -1;

    // request constants
    private static final String DEFAULT_PATH = "/";
    private static final String DEFAULT_LOCATION = "/index.html";

    // response constants
    private static final String STRING_TO_BYTE_CHARSET = "US-ASCII";

    private static final int TIMEOUT_CODE = 408;
    private static final String TIMEOUT_PHRASE = "Request Timeout";
    private static final int BAD_CODE = 400;
    private static final String BAD_PHRASE = "Bad Request";
    private static final int NOT_FOUND_CODE = 404;
    private static final String NOT_FOUND_PHRASE = "Not Found";

    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String HTTP_METHOD = "GET";
    private static final String EOL = "\r\n";
    private static final String END_OF_HEADERS = EOL;

    // connection variables
    private String serverName;
    private String root;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private int timeout;
    private String objectPath;

    /**
     * @param serverName
     * @param root
     * @param socket
     * @param timeout
     */
    public WorkerThread(String serverName, String root, Socket socket, int timeout) {
        this.serverName = serverName;
        this.root = root;
        this.socket = socket;
        this.timeout = timeout;
        this.objectPath = null;

        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        boolean wasSuccessful = true;

        try {
            socket.setSoTimeout(timeout);

            boolean requestFormattedCorrectly = parseRequest();
            if (!requestFormattedCorrectly) {
                sendResponse(constructResponse(BAD_CODE, BAD_PHRASE, false));
            }

            boolean isObjectFound = isObjectFound();
            if (!isObjectFound) {
                sendResponse(constructResponse(NOT_FOUND_CODE, NOT_FOUND_PHRASE, false));
            }

            
        } 
        catch (SocketTimeoutException e) {
            e.printStackTrace();
            sendResponse(constructResponse(TIMEOUT_CODE, TIMEOUT_PHRASE, false));
        }
        catch (SocketException e) {
            wasSuccessful = false;
            e.printStackTrace();
        } 
        catch (IOException e) {
            wasSuccessful = false;
            e.printStackTrace();
        }
        /*
         * We must always close our resources, regardless of whether or not 
         * the request was successful. We close them in reverse chronological 
         * order.
         */
        finally {
            closeGracefully(
                // fileOutputStream,
                outputStream,
                inputStream,
                socket
            );
            if (!wasSuccessful) {
                System.exit(UNSUCCESSFUL_TERMINATION);
            } 
        }
    }

     /**
     * Close all opened streams, sockets, and other resources before terminating the program.
     *
     * @param resources all resources which need to be closed
     */
    private void closeGracefully(Closeable... resources) {
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

    private boolean parseRequest() throws IOException {
        /*
            initially, we have not read any bytes from the request. We need
            both prevByte and currByte because we need to keep track of two
            bytes sequentially (to see if we've encountered \r\n).
        */
        int prevByte = NO_BYTE;
        int currByte = NO_BYTE;
        
        /*  the request line is formatted as: method objectPath Protocol
            when we split on the request line, we use an index of 0, 1, and 2 to access the code and phrase
            respectively
         */
        int METHOD = 0;
        int OBJECT_PATH = 1;
        int PROTOCOL = 2;

        /*
         * We haven't yet read the request, so the current line and the request are empty. 
         * In addition, we need a separate boolean for knowing if we are on the firstLine, because this is how
         * we know to check for the components of the request line.  
         */
        String currLine = "";
        boolean readFirstLine = false;
        String request = "";
 
        // assume the request was properly formatted unless we find otherwise
        boolean requestFormattedCorrectly = true;

        while ((currByte = inputStream.read()) != EOF) {
            currLine += (char) currByte;

            /* 
                * Java implicitly converts the char to its ASCII value when
                * comparing with prevByte and currByte. This is why we are able 
                * to make this comparison.
                */
            if (prevByte == '\r' && currByte == '\n') {
                request += currLine;

                /*
                * Check that the request line acts as we expect. 
                * We need to trim to get rid of extra white space before
                * or after (otherwise we'll parse the request incorrectly).
                */
                if (!readFirstLine) {
                    String[] requestLine = currLine.split(" ");

                    if (requestLine.length != 3 ||
                        !requestLine[METHOD].trim().equals(HTTP_METHOD) ||
                        !requestLine[PROTOCOL].trim().equals(HTTP_VERSION) ||
                        !requestLine[OBJECT_PATH].trim().startsWith("/")
                    ) {
                        /* 
                            if the request is improperly formatted, we set the boolean to false but
                            do not immediately break out of the loop. This is because we still 
                            need to finish reading all headers.
                        */
                        requestFormattedCorrectly = false;
                    } 
                    else {
                        // handle the case where no object-path is provided
                        objectPath = requestLine[OBJECT_PATH].trim();
                        if (objectPath.equals(DEFAULT_PATH)) {
                            objectPath = DEFAULT_LOCATION;
                        }
                    }
                    readFirstLine = true;
                }
                
                /*
                    * If we've encountered a line consisting solely of \r\n, this means
                    * we've found the end of our header lines. We can exit the loop
                    * as we no longer want to parse the remainder of the request (since it is
                    * a get, it should not have a request body).
                    */
                if (currLine.equals("\r\n")) {
                    break;
                }

                /* 
                    * If you've found the end of a regular header line, then "reset"
                    * it to begin reading the next header line. This is to ensure that
                    * we do not add duplicate or redundant info to our request when
                    * printing to console. 
                    */
                currLine = "";
                prevByte = NO_BYTE;
                currByte = NO_BYTE;
            }

            prevByte = currByte;
        }

        System.out.println(request);
        return requestFormattedCorrectly;
    }

    private boolean isObjectFound() {
        File file = new File(root, objectPath);
        return file.exists() && file.isFile();
    }

    private void sendResponse(String response) {
        try {
            byte[] responseBytes = response.getBytes(STRING_TO_BYTE_CHARSET);
            outputStream.write(responseBytes);
            // flush to ensure response is actually written to the client.
            outputStream.flush();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
            
        System.out.println(response);
    }

    /**
     * Create a properly formatted HTTP response message.
     *
     * @return A response message that is ready to send to the client.
     */
    private String constructResponse(int httpStatusCode, String httpStatusPhrase, boolean isOK) {
        String statusLine = HTTP_VERSION + " " + httpStatusCode + " " + httpStatusPhrase + EOL;
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
