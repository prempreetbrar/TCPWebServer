/**
 * WebServer Class
 *
 * Implements a multi-threaded web server supporting non-persistent connections.
 */

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;


public class WebServer extends Thread {
	// global logger object, configures in the driver class
	private static final Logger logger = Logger.getLogger("WebServer");
    private static final String SERVER_NAME = "Prempreet's Server";

    // check if the server was shutdown every 100ms; in the case of infinite timeout, 
    // shutdown the server after 1 second
    private static final int CHECK_SHUTDOWN_INTERVAL = 100;
    private static final long DEFAULT_SERVER_SHUTDOWN_TIME = 1000;
    private static final int INFINITE = 0;

	private boolean shutdown = false; // shutdown flag
    private int port;
    private String root;
    private int timeout;
    private long serverShutdownTime;
    private ExecutorService executorService;
	
    /**
     * Constructor to initialize the web server
     * 
     * @param port 	Server port at which the web server listens > 1024
	 * @param root	Server's root file directory
	 * @param timeout	Idle connection timeout in milli-seconds
     * 
     */
	public WebServer(int port, String root, int timeout) {
        this.port = port;
        this.root = root;
        this.timeout = timeout;

        /*
         * To deal with the case where the timeout is infinite, we cannot just wait
         * an infinite amount of time for threads to terminate. In that case, wait a 
         * reasonable fixed amount of time. 
         */
        if (timeout == INFINITE) {
            serverShutdownTime = DEFAULT_SERVER_SHUTDOWN_TIME;
        } 
        else {
            serverShutdownTime = timeout;
        }

        this.executorService = Executors.newCachedThreadPool();
    }

	
    /**
	 * Main method in the web server thread.
	 * The web server remains in listening mode 
	 * and accepts connection requests from clients 
	 * until it receives the shutdown signal.
	 * 
     */
	public void run() {
        /*
         * We need to keep track of all worker threads, but not all sockets opened up for clients.
         * This is because the worker threads themselves will close each individual client socket. 
         */
        ServerSocket serverSocket = null;

        /* 
         * if we can't even open a server socket or configure its shutdown interval, 
         * then we need to terminate the program immediately.
        */ 
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(CHECK_SHUTDOWN_INTERVAL);
        } 
        catch (IOException e) {
            e.printStackTrace();
            cleanup(serverSocket);
        }

        while (!shutdown) {
            try {
                Socket newSocketForClient = serverSocket.accept();
                System.out.println("New connection from " + newSocketForClient.getInetAddress() +
                                   ":" + newSocketForClient.getPort() + Utils.EOL);
                WorkerThread workerThread = new WorkerThread(SERVER_NAME, root, newSocketForClient, timeout);
                executorService.submit(workerThread);
            } 
            /*
             * This is expected behaviour; we do not terminate the program if there is a timeout.
             * We simply check the loop condition again.
             */
            catch (SocketTimeoutException e) {

            } 
            catch (IOException e) {
                /* 
                 * This is an error in accepting a connection from A client; however, the server
                 * can still recover from this error (it can just ignore it and continue to try
                 * accepting connections from future clients). As a result, we don't terminate the program.
                 */
                e.printStackTrace();
            } 
        }
        cleanup(serverSocket);
    }

    /**
     * Close all opened streams, sockets, and other resources before terminating the program.
     *
     * @param serverSocket the serverSocket listening for connections
     */
    private void cleanup(ServerSocket serverSocket) {
        try {
            /*
             * The java documentation was interpreted as follows: 
             *   .shutdown() is an orderly shutdown in which previously submitted; gives the
             *      worker threads a chance to finish.
             *   .awaitTermination() waits a certain amount of time after .shutdown(); in other
             *    words, the "chance" the worker threads are given is limited to a specific time
             *    period.
             *   .shutdownNow() forcefully kills all worker threads that have still not terminated 
             *    after the wait period.
             * 
             * Therefore, all three methods are necessary:
             * 1. Initiate shutdown
             * 2. Limit shutdown to a certain period of time (so that we are not waiting forever)
             * 3. Terminate threads that did not shut down within that certain period of time. 
             * 
             * This is necessary if the server takes a long time to transmit the file (for example,
             * if you set the buffer size to be 1, and then quit the server, without shutdownNow
             * the program abruptly stops after waiting for termination. However, with shutdownNow, 
             * all workerThreads invoke their cleanup method by throwing an InterruptedException). 
             * Without shutdownNow, the server will just stop, and there is no guarantee the workerThreads invoked
             * their cleanup methods. shutdownNow forces this invocation by toggling the interrupt flag.
             */
            executorService.shutdown();
            executorService.awaitTermination(serverShutdownTime, TimeUnit.MILLISECONDS);
            executorService.shutdownNow();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        Utils.closeGracefully(serverSocket);
    }

    /**
     * Signals the web server to shutdown.
	 *
     */
	public void shutdown() {
		shutdown = true;
	}
	
}
