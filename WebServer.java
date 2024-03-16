

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
import java.util.concurrent.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class WebServer extends Thread {
	// global logger object, configures in the driver class
	private static final Logger logger = Logger.getLogger("WebServer");
    private static final String SERVER_NAME = "Prempreet's Server";

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
        while (!shutdown) {
            try {
                listen();
            }
        }
    }

    private void listen() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {
            Socket newSocketForClient = serverSocket.accept();
            WorkerThread workerThread = new WorkerThread(SERVER_NAME, newSocketForClient, timeout);
        }
    }
	

    /**
     * Signals the web server to shutdown.
	 *
     */
	public void shutdown() {
		shutdown = true;
	}
	
}
