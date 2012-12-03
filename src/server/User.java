package server;

import java.net.Socket;

public class User extends Client {
	
	private String userName;
	private String userID;

	public User(Socket clientSocket) {
		super(clientSocket);
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserID() {
		return userID;
	}

	public void setUserID(String userID) {
		this.userID = userID;
	}
	
}