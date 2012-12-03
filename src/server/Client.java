package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
	public BufferedReader inputReader;
	public Socket clientSocket;
	public PrintWriter outputWriter;

	public Client(Socket clientSocket) {
		try {
			this.clientSocket = clientSocket;
			inputReader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
			outputWriter = new PrintWriter(this.clientSocket.getOutputStream(), true);
		} catch (Exception e) {
			System.out.println("Error beginning StreamReader: " + e.getMessage());
		}
	}
}
