/**
 * WorkerThread Class
 * 
 * CPSC 441 - L01 - T01
 * Assignment 3
 * 
 * TA: Amir Shani
 * Student: Prempreet Brar
 * UCID: 30112576
 * 
 * A single thread that services a GET request.
 */

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
    private int timeout;
    private String objectPath;
    private InputStream inputStream;
    private OutputStream outputStream;
    private FileInputStream fileInputStream;

    /**
     * @param serverName // name of server used in response headers
     * @param root // root directory of the web server where objects are located
     * @param socket // socket established with client over which communication takes place
     * @param timeout // time period after which connection closes if no request received
     */
    public WorkerThread(String serverName, String root, Socket socket, int timeout) {
        this.serverName = serverName;
        this.root = root;
        this.socket = socket;
        this.timeout = timeout;
        
        this.objectPath = null;
        this.inputStream = null;
        this.outputStream = null;
    }

    /*
     * If shutdownNow was invoked by the server, this thread could have been interrupted.
     * We need to periodically check for such interruptions so that we can terminate all execution
     * immediately if needed.
     * 
     * The exception causes the run method to enter the "finally" block and clean up the thread.
     * This is a clever use of Java's exception handling functionality. 
     */
    private void checkInterruption() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }

    /**
     * method that parses the client request and responds appropriately.
     */
    public void run() {
        try {
            // included here so we don't need an exception block; could just as easily
            // have been initialized in the constructor
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
            socket.setSoTimeout(timeout);

            // bad request?
            boolean requestFormattedCorrectly = parseRequest();
            if (!requestFormattedCorrectly) {
                sendResponse(constructResponseInfo(Utils.BAD_CODE, Utils.BAD_PHRASE, 
                                              false, null), null);
                return;
            }

            // object exists?
            File object = obtainObject();
            if (object == null) {
                sendResponse(constructResponseInfo(Utils.NOT_FOUND_CODE, Utils.NOT_FOUND_PHRASE, 
                                              false, null), null);
                return;
            }
            
            // send the object back
            sendResponse(constructResponseInfo(Utils.OK_CODE, Utils.OK_PHRASE, true, object), object);
        } 
        catch (SocketTimeoutException e) {
            e.printStackTrace();
            // for formatting the console to look cleaner
            System.out.println();

            /*
             * sending a response itself can generate an exception, which is why we have another try-catch block
             * inside of the timeout catch. Note that this is necessary: we can only be certain the connection timed out
             * if we are inside this block, and can thus only construct and send the response inside this block. 
             */
            try {
                sendResponse(constructResponseInfo(Utils.TIMEOUT_CODE, Utils.TIMEOUT_PHRASE, 
                                                   false, null), null);
            } 
            catch (IOException e1) {
                e1.printStackTrace();
            } 
            catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
        catch (SocketException e) {
            e.printStackTrace();
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        /*
         * We must always close our resources, regardless of whether or not 
         * the request was successful. We close them in reverse chronological 
         * order.
         */
        finally {
            Utils.closeGracefully(
                fileInputStream,
                outputStream,
                inputStream,
                socket
            );
        }
    }

    /**
     * Parse the incoming HTTP request.
     *
     * @throws IOException 
     * @throws InterruptedException 
     * @return boolean denoting whether request is correctly formatted
     */
    private boolean parseRequest() throws IOException, InterruptedException {
        /*
            initially, we have not read any bytes from the request. We need
            both prevByte and currByte because we need to keep track of two
            bytes sequentially (to see if we've encountered \r\n).
        */
        int prevByte = Utils.NO_BYTE;
        int currByte = Utils.NO_BYTE;
        
        /*  the request line is formatted as: method objectPath Protocol
            when we split on the request line, we use an index of 0, 1, and 2 to access the components
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
            checkInterruption();
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
                        !requestLine[OBJECT_PATH].trim().startsWith(Utils.DEFAULT_PATH)
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

        checkInterruption();
        System.out.println(request);
        return requestFormattedCorrectly;
    }

    /**
     * Obtain the requested object from the root directory. 
     *
     * @return A file object (or nothing if the file doesn't exist) back to the client. 
     * @throws InterruptedException 
     */
    private File obtainObject() throws InterruptedException {
        checkInterruption();

        // we need to check that the user didn't just give us a directory
        File file = new File(root, objectPath);
        if (file.exists() && file.isFile()) {
            return file;
        }
        return null;
    }

    /**
     * Send a response (both the header info and the requested object).
     * 
     * @param responseInfo // status line and headers
     * @param responseObject // requested object
     * @throws IOException 
     * @throws InterruptedException 
     */
    private void sendResponse(String responseInfo, File responseObject) throws IOException, InterruptedException {
        // the status line and header are a string so we know we can easily convert them to bytes
        byte[] responseBytes = responseInfo.getBytes(Utils.STRING_TO_BYTE_CHARSET);
        outputStream.write(responseBytes);
        checkInterruption();

        /*
        * The numBytes tells us how many bytes to actually write to the stream; this may
        * be different from the buffer size (ie. if the number of bytes remaining is <
        * buffer.length). This is why we cannot specify buffer.length as the number of bytes being written,
        * as we would get an IndexOutOfBounds exception when we reach the end.
        * 
        * The file could be of any format, so we need to actually read it using an input stream.
        */
        if (responseObject != null) {
            int numBytes = 0;
            byte[] buffer = new byte[Utils.BUFFER_SIZE];
            fileInputStream = new FileInputStream(responseObject);
    
            while ((numBytes = fileInputStream.read(buffer)) != Utils.EOF) {
                checkInterruption();
                outputStream.write(buffer, Utils.OFFSET, numBytes);
            }    
        }

        // flush to ensure response is actually written to the client.
        checkInterruption();
        outputStream.flush();
        System.out.println(responseInfo);
    }

    /**
     * Create properly formatted HTTP response info (excluding file content).
     *
     * @param httpStatusCode status code of get request
     * @param httpStatusPhrase status phrase of get request
     * @param isOK whether request is 200 (which influences the headers we add)
     * @param file the requested file object (may be empty if request failed)
     * @return Response info (excluding file content) ready to be sent to client.
     * @throws IOException 
     * @throws InterruptedException 
     */
    private String constructResponseInfo(int httpStatusCode, String httpStatusPhrase, boolean isOK, File file) throws IOException, InterruptedException {
        String statusLine = Utils.HTTP_VERSION + " " + httpStatusCode + " " + httpStatusPhrase + Utils.EOL;
        String headers = constructHeaders(isOK, file);

        /*
         * A response message has four "components"; this is why the code is broken up
         * in a similar manner, but these could just as easily be constructed as a single string.
         */
        String response = statusLine + headers + Utils.END_OF_HEADERS;
        checkInterruption();
        return response;
    }

    /**
     * Create properly formatted HTTP header lines.
     *
     * @param isOK whether request is 200 (which influences the headers we add)
     * @param file the requested file object (may be empty if request failed)
     * @return Header lines ready to be included in the HTTP response.
     * @throws IOException 
     * @throws InterruptedException 
     */
    private String constructHeaders(boolean isOK, File file) throws IOException, InterruptedException {
        String date = "Date: " + ServerUtils.getCurrentDate() + Utils.EOL;
        String server = "Server: " + serverName + Utils.EOL;
        String connection = "Connection: close" + Utils.EOL;

        if (isOK) {
            String lastModified = "Last-Modified: " + ServerUtils.getLastModified(file) + Utils.EOL;
            String contentLength = "Content-Length: " + ServerUtils.getContentLength(file) + Utils.EOL;
            String contentType = "Content-Type: " + ServerUtils.getContentType(file) + Utils.EOL;
            
            checkInterruption();
            return date + server + lastModified + contentLength + contentType + connection;
        } 
        checkInterruption();
        return date + server + connection; 
    }
}
