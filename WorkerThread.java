import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class WorkerThread extends Thread {
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
                sendResponse(constructResponseInfo(Utils.BAD_CODE, Utils.BAD_PHRASE, false, null), null);
            }

            File object = obtainObject();
            if (object == null) {
                sendResponse(constructResponseInfo(Utils.NOT_FOUND_CODE, Utils.NOT_FOUND_PHRASE, false, null), null);
            }

            sendResponse(constructResponseInfo(Utils.OK_CODE, Utils.OK_PHRASE, true, object), object);
        } 
        catch (SocketTimeoutException e) {
            e.printStackTrace();
            try {
                sendResponse(constructResponseInfo(Utils.TIMEOUT_CODE, Utils.TIMEOUT_PHRASE, false, null), null);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
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
            Utils.closeGracefully(
                // fileOutputStream,
                outputStream,
                inputStream,
                socket
            );
            if (!wasSuccessful) {
                System.exit(Utils.UNSUCCESSFUL_TERMINATION);
            } 
        }
    }


    private boolean parseRequest() throws IOException {
        /*
            initially, we have not read any bytes from the request. We need
            both prevByte and currByte because we need to keep track of two
            bytes sequentially (to see if we've encountered \r\n).
        */
        int prevByte = Utils.NO_BYTE;
        int currByte = Utils.NO_BYTE;
        
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

        while ((currByte = inputStream.read()) != Utils.EOF) {
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
                        !requestLine[METHOD].trim().equals(Utils.HTTP_METHOD) ||
                        !requestLine[PROTOCOL].trim().equals(Utils.HTTP_VERSION) ||
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
                        if (objectPath.equals(Utils.DEFAULT_PATH)) {
                            objectPath = Utils.DEFAULT_LOCATION;
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
                prevByte = Utils.NO_BYTE;
                currByte = Utils.NO_BYTE;
            }

            prevByte = currByte;
        }

        System.out.println(request);
        return requestFormattedCorrectly;
    }

    private File obtainObject() {
        File file = new File(root, objectPath);
        if (file.exists() && file.isFile()) {
            return file;
        }
        return null;
    }

    private void sendResponse(String responseInfo, File responseObject) {
        try {
            byte[] responseBytes = responseInfo.getBytes(Utils.STRING_TO_BYTE_CHARSET);
            outputStream.write(responseBytes);

            /*
            * The numBytes tells us how many bytes to actually write to the stream; this may
            * be different from the buffer size (ie. if the number of bytes remaining is <
            * buffer.length). This is why we cannot specify buffer.length as the number of bytes being written,
            * as we would get an IndexOutOfBounds exception when we reach the end.
            */
            int numBytes = 0;
            byte[] buffer = new byte[Utils.BUFFER_SIZE];
            FileInputStream fileInputStream = new FileInputStream(responseObject);

            while ((numBytes = fileInputStream.read(buffer)) != Utils.EOF) {
                outputStream.write(buffer, Utils.OFFSET, numBytes);
            }
            // flush to ensure response is actually written to the client.
            outputStream.flush();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
            
        System.out.println(responseInfo);
    }

    /**
     * Create a properly formatted HTTP response message.
     *
     * @return A response message that is ready to send to the client.
     * @throws IOException 
     */
    private String constructResponseInfo(int httpStatusCode, String httpStatusPhrase, boolean isOK, File file) throws IOException {
        String statusLine = Utils.HTTP_VERSION + " " + httpStatusCode + " " + httpStatusPhrase + Utils.EOL;
        String headers = constructHeaders(isOK, file);

        /*
         * A response message has four "components"; this is why the code is broken up
         * in a similar manner, but these could just as easily be constructed as a single string.
         */
        String response = statusLine + headers + Utils.END_OF_HEADERS;
        return response;
    }

    private String constructHeaders(boolean isOK, File file) throws IOException {
        String date = "Date: " + ServerUtils.getCurrentDate() + Utils.EOL;
        String server = "Server: " + serverName + Utils.EOL;
        String connection = "Connection: close" + Utils.EOL;

        if (isOK) {
            String lastModified = "Last-Modified: " + ServerUtils.getLastModified(file) + Utils.EOL;
            String contentLength = "Content-Length: " + ServerUtils.getContentLength(file) + Utils.EOL;
            String contentType = "Content-Type: " + ServerUtils.getContentType(file) + Utils.EOL;
            return date + server + lastModified + contentLength + contentType + connection;
        } 
        return date + server + connection; 
    }
}
