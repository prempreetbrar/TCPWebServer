

/**
 * WebServer Class
 * 
 * CPSC 441 - L01 - T01
 * Assignment 3
 * 
 * TA: Amir Shani
 * Student: Prempreet Brar
 * UCID: 30112576
 * 
 * Implements a multi-threaded web server supporting non-persistent connections.
 *
 */

import java.util.*;
import java.util.logging.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;


public class WebServer extends Thread {
	// global logger object, configures in the driver class
	private static final Logger logger = Logger.getLogger("WebServer");
    private static final String SERVER_NAME = "Prempreet's Server";
    private static final int CHECK_SHUTDOWN_INTERVAL = 100;

	private boolean shutdown = false; // shutdown flag
    private int port;
    private String root;
    private int timeout;
	
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
        List<Thread> workerThreads = new ArrayList<>();

        while (!shutdown) {
            try {
                serverSocket = new ServerSocket(port);
                serverSocket.setSoTimeout(CHECK_SHUTDOWN_INTERVAL);

                Socket newSocketForClient = serverSocket.accept();
                WorkerThread workerThread = new WorkerThread(SERVER_NAME, root, newSocketForClient, timeout);
                workerThreads.add(workerThread);
                workerThread.start();
            } 
            /*
             * This is expected behaviour; we do not terminate the program if there is a timeout.
             * We simply check the loop condition again.
             */
            catch (SocketTimeoutException e) {
                e.printStackTrace();
            } 
            // breaking allows us to exit the loop and clean up
            catch (SocketException e) {
                e.printStackTrace();
                break;
            } 
            catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        /*
         * workerThreads.toArray converts all worker threads to an array of workerthread objects.
         * The size of this array is the number of worker threads we currently have running. However,
         * when creating this array, we have to assign a type. This is why we say "new WorkerThread".
         * 
         * In summary: We create a fixed array from our arraylist that contains all the original worker
         * threads and is of type WorkerThread[]. A fixed size array can be passed as a "varargs"
         * argument, which is what joinGracefully accepts.
         */
        Utils.joinGracefully(workerThreads.toArray(new WorkerThread[workerThreads.size()]));
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
