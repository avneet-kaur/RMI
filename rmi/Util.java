package rmi;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Util {

	public static void closeSocketConnection(Socket socket) {
		try {
			socket.close();
		} catch (IOException e) {
			throw new RuntimeException("Getting an error while closing socket", e);
		}
	}

	public static void closeServerSocketConnection(ServerSocket serverSocket) {
		try {
			serverSocket.close();
		} catch (IOException e) {
			throw new RuntimeException("Getting an error while closing server socket", e);
		}
	}

}
