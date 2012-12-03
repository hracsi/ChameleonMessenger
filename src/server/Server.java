package server;

import java.awt.EventQueue;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JList;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.SystemColor;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.border.EtchedBorder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import data.Commands;

import java.awt.Font;
import java.awt.Toolkit;


public class Server {

	private JFrame frmChameleonMessengerServer;
	private JButton btnStartServer = new JButton("Start Server!");
	private JButton btnStopServer = new JButton("Stop Server!");
	private JButton btnClearMessages = new JButton("Clear messages");
	private JLabel lblOnlineUsersList = new JLabel("Online users list:");
	private JTextArea serverMessagesArea = new JTextArea();
	private DefaultListModel usersList = new DefaultListModel();
	private JList onlineUsersList = new JList(usersList);
	
	private boolean serverStatus = false;
	protected ServerSocket loginSocket;
	protected ServerSocket userSocket;
	protected ArrayList<User> listOfUsers = new ArrayList<User>();
 
	
	public class LoginHandler implements Runnable {
		
		private Client client;
		
		public LoginHandler(Socket clientSocket) {
			client = new Client(clientSocket);
		}
			   
		public void run() {
			String message = null;
			try {
				while ((message = client.inputReader.readLine()) != null) {
					
					JSONParser parser = new JSONParser();
					JSONObject jsonMessage = (JSONObject) parser.parse(message);
					String command = (String) jsonMessage.get("command");
					Commands c = Commands.valueOf(command.toUpperCase());

					switch(c) {
						case LOGIN:
							String email = (String) jsonMessage.get("email");
							String password = (String) jsonMessage.get("password");
							verifyLogin(client, email, password);
						break;
						default:
							serverMessagesArea.append("Default: \n" + message);
							serverMessagesArea.setCaretPosition(serverMessagesArea.getDocument().getLength());
						break;
					}					
					client.outputWriter.flush();
				}
			} catch (Exception e) {
				System.out.println("Client run exception: \n" + e.getCause());
			}
		}
	}
	
	public class UserHandler implements Runnable {
		
		private User user;
		
		public UserHandler(Socket userSocket) {
			user = new User(userSocket);
		}
			   
		@SuppressWarnings("unchecked")
		public void run() {
			String message = null;
			try {
				while ((message = user.inputReader.readLine()) != null) {
					
					JSONParser parser = new JSONParser();
					JSONObject jsonMessage = (JSONObject) parser.parse(message);
					String command = (String) jsonMessage.get("command");
					Commands c = Commands.valueOf(command.toUpperCase());

					switch(c) {
						case NEWUSER:
							//It's a valid online user so adding to the list
							listOfUsers.add(user);
							
							//Setting users id to successfully identify him later
							String id = (String) jsonMessage.get("id");
							user.setUserID(id);

							//Getting the user's data and set it to the object
							String userData = getUserData(user, id);
							JSONObject jsonUserData = (JSONObject) parser.parse(userData);
							String userName = (String) jsonUserData.get("name");
							user.setUserName(userName);

							//Let know other users that a new user has just connected
							JSONObject newUserMsg = new JSONObject();
				        	newUserMsg.put("command", "message");
				        	newUserMsg.put("visibility", "server");
				        	newUserMsg.put("message", user.getUserName() + " has just joined to the messenger!");
				        	String newUserMsgJSON = newUserMsg.toJSONString();
				        	sendToAll(newUserMsgJSON);
				        	
				        	//Add the user to the list box
				        	usersList.addElement(userName);
				        	sendUsersList();
						break;
						case MESSAGE:
							String name = (String) jsonMessage.get("name");
							String visibility = (String) jsonMessage.get("visibility");
							String msg = (String) jsonMessage.get("message");
							serverMessagesArea.append(name + " sent a message to " + visibility + " with this text:\n" + msg + "\n\n");
							serverMessagesArea.setCaretPosition(serverMessagesArea.getDocument().getLength());
							
							sendToAll(message);
						break;
						case USERDATA:
							String userId = (String) jsonMessage.get("id");
							getUserData(user, userId);
						break;
						case DISCONNECT:
							String disconnectId = (String) jsonMessage.get("id");
							removeFromUsers(disconnectId);
							sendUsersList();
						break;
						default:
							serverMessagesArea.append("\n" + message);
							serverMessagesArea.setCaretPosition(serverMessagesArea.getDocument().getLength());
						break;
					}					
					user.outputWriter.flush();
				}
			} catch (Exception e) {
				removeFromUsers(user.getUserID());
				sendUsersList();
				System.out.println("Client run exception: " + e.getCause());
			}
		}
	}
	
	public class StartServer implements Runnable{

		private final Integer DEFAULT_LOGIN_PORT = 666;
		private final Integer DEFAULT_USER_PORT = 667;
		
		public StartServer() {
			super();
			try {
				loginSocket = new ServerSocket(DEFAULT_LOGIN_PORT);
				userSocket	= new ServerSocket(DEFAULT_USER_PORT);
				addServerMessage("Server has been started.");
				setServerStatus(true);
			} catch (Exception e) {
				addServerMessage("Could not start the server on these ports: " + DEFAULT_LOGIN_PORT + " " + DEFAULT_USER_PORT  + "!");
				setServerStatus(false);
				resetServer();
			}
		} 

		public void run() {
			while ( getServerStatus() ) {
				try {
					Socket clientSocket = loginSocket.accept();
					Thread client = new Thread(new LoginHandler(clientSocket));
					client.start();

					Socket userSock = userSocket.accept();
					Thread user = new Thread(new UserHandler(userSock));
					user.start();
				} catch (Exception e) {
					setServerStatus(false);
				}
			}
		}

	}
	
	@SuppressWarnings("unchecked")
	private void verifyLogin(Client client, String email, String password) {
		String link = linkMaker("login", "email", email, "password", password);
		HttpClient httpClient = new DefaultHttpClient();
	    HttpPost request = new HttpPost(link);
        request.addHeader("content-type", "application/x-www-form-urlencoded");
        
		try {
			HttpResponse response = httpClient.execute(request);
			HttpEntity postResponseEntity = response.getEntity();
        
	        if (postResponseEntity != null) {
	        	String result = EntityUtils.toString(postResponseEntity);
				
	        	//incoming JSON data
	        	JSONParser parser = new JSONParser();
	        	JSONObject jsonResponse = (JSONObject) parser.parse(result);
	        	String verify = (String) jsonResponse.get("verify");
	
	        	//output JSON data
	        	JSONObject verifyLogin = new JSONObject();
	    		verifyLogin.put("command", "verifyLogin");
	    		
	    		//if the email and password was correct
	        	if ( verify.equals("valid") ) {
	        		String validId = (String) jsonResponse.get("id");
	        		String name = (String) jsonResponse.get("name");
	        		
	        		//output JSON data
	        		verifyLogin.put("id", validId);
	        		
	        		//checking whether the user is already logged in
	        		Iterator<User> users = listOfUsers.iterator();
	        		while (users.hasNext()) {
	        			User user = users.next();
	        			String id = user.getUserID();
	        			if ( validId.equals(id) ) {
	        				verify = "alreadyLoggedIn";
	        				break;
	        			}
	        		}

	        		if ( verify.equals("valid") ) {
	        			serverMessagesArea.append(name + " has successfuly logged in!\n");
	        		} else {
	        			verifyLogin.put("name", name);
	        			serverMessagesArea.append(name + " has tried to log in again!\n");
	        		}
	        	} else {
	        		serverMessagesArea.append("Login was unsuccessful with:" + email + " " + password + "\n");
	        	}
	        	serverMessagesArea.setCaretPosition(serverMessagesArea.getDocument().getLength());
	        	
	        	verifyLogin.put("verify", verify);
	        	String json = verifyLogin.toJSONString();
	        	System.out.print(json);
	        	client.outputWriter.print(json + "\n");
	        	client.outputWriter.flush();
	        }
		} catch (Exception e) {
			System.out.print("IO Exception: \n");
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	private String getUserData(User user, String id) {
		String link = linkMaker("userdata", "id", id, "", "");
		HttpClient httpClient = new DefaultHttpClient();
	    HttpPost request = new HttpPost(link);
        request.addHeader("content-type", "application/x-www-form-urlencoded");
        System.out.println("\n"+link);
		try {
			HttpResponse response = httpClient.execute(request); 
			HttpEntity postResponseEntity = response.getEntity();
	        if (postResponseEntity != null) {
	        	String result = EntityUtils.toString(postResponseEntity);
	        	//incoming JSON data
	        	System.out.println("\n"+result);
	        	JSONParser parser = new JSONParser();
	        	JSONObject jsonResponse = (JSONObject) parser.parse(result);
				String name = (String) jsonResponse.get("name");
	
	        	//output JSON data
	        	JSONObject userData = new JSONObject();
	        	userData.put("command", "userdata");
	        	userData.put("name", name);
        	
	        	String json = userData.toJSONString();
	        	System.out.print(json);
	        	user.outputWriter.print(json + "\n");
	        	user.outputWriter.flush();
	        	
	        	return json;
	        }
		} catch (Exception e) {
			System.out.print("Exception: \n");
			e.printStackTrace();
		}
		return "error";
	}
	
	@SuppressWarnings("unchecked")
	private void sendUsersList() {
		if ( usersList.size() > 0 ) {
			JSONArray users = new JSONArray();
			for(int i = 0; i < usersList.size(); i++) {
				users.add(usersList.get(i));
			}
			JSONObject obj = new JSONObject();
			obj.put("command","userslist");
			obj.put("users", users);
			String jsonUsersList = obj.toJSONString();
			sendToAll(jsonUsersList);
		}
	}
	
	private void removeFromUsers(String id) {
		String userName = "";
		Iterator<User> users = listOfUsers.iterator();
		while (users.hasNext()) {
			User user = users.next();
			if (user.getUserID().equals(id) ) {
				userName = user.getUserName();
				users.remove();
			}
		}
		
		for(int i = 0; i < usersList.size(); i++) {
			if ( usersList.elementAt(i).equals(userName) ) {
				usersList.removeElementAt(i);
				break;
			}
		}
		
	}
	
	private void sendToAll(String message) {
		Iterator<User> users = listOfUsers.iterator();
		while (users.hasNext()) {
			User user = users.next();
			user.outputWriter.print(message + "\n");
			user.outputWriter.flush();
		}
	}
	
	private String linkMaker(String command, String param1Name, String param1, String param2Name, String param2) {
		String ret = null;
		if ( param2Name.equals("") ) {
			ret = new String("http://test.hracsi.net/json.php?command=" + command + "&" + param1Name + "=" + param1);
		} else {
			ret = new String("http://test.hracsi.net/json.php?command=" + command + "&" + param1Name + "=" + param1 + "&" + param2Name + "=" + param2);	
		}
		return ret;
	}
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
		    @Override
		    public void run() {
		        closeEvent();
		    }
		});
		
		try {
	        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	    } catch (Exception e) {
	    	System.out.println("Couldn't set the look!");
	    	e.printStackTrace();
	    }
		
		EventQueue.invokeLater(new Runnable()
		{
			public void run() {
				try {
					Server window = new Server();
					window.frmChameleonMessengerServer.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Creating the GUI elements
	 */
	public Server() {
		frmChameleonMessengerServer = new JFrame();
		frmChameleonMessengerServer.setTitle("Chameleon Messenger Server");
		frmChameleonMessengerServer.setIconImage(Toolkit.getDefaultToolkit().getImage(Server.class.getResource("/server/server.png")));
		frmChameleonMessengerServer.getContentPane().setBackground(SystemColor.inactiveCaptionBorder);
		frmChameleonMessengerServer.setBackground(SystemColor.activeCaption);
		frmChameleonMessengerServer.setBounds(100, 100, 900, 600);
		frmChameleonMessengerServer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmChameleonMessengerServer.getContentPane().setLayout(null);
		
		
		btnStartServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				btnStopServer.setEnabled(true);
				btnStartServer.setEnabled(false);
				Thread serverThread = new Thread(new StartServer());
				serverThread.start();
			}
		});
		btnStartServer.setBounds(22, 18, 102, 23);
		frmChameleonMessengerServer.getContentPane().add(btnStartServer);
		btnStopServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				resetServer();
			}
		});
		
		btnStopServer.setEnabled(false);
		btnStopServer.setBounds(134, 18, 102, 23);
		frmChameleonMessengerServer.getContentPane().add(btnStopServer);
		serverMessagesArea.setFont(new Font("Courier New", Font.PLAIN, 14));
		
		
		JScrollPane scrollPane = new JScrollPane(serverMessagesArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setAutoscrolls(true);
		scrollPane.setBounds(22, 50, 579, 454);
		frmChameleonMessengerServer.getContentPane().add(scrollPane);
		
		serverMessagesArea.setLineWrap(true);
		scrollPane.setViewportView(serverMessagesArea);
		serverMessagesArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				checkServerMessages();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				checkServerMessages();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				checkServerMessages();
			}
		});
		
		onlineUsersList.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		onlineUsersList.setBounds(626, 50, 248, 453);
		frmChameleonMessengerServer.getContentPane().add(onlineUsersList);
		btnClearMessages.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearServerMessages();
			}
		});
		
		btnClearMessages.setEnabled(false);
		btnClearMessages.setBounds(22, 507, 115, 23);
		frmChameleonMessengerServer.getContentPane().add(btnClearMessages);
		
		
		lblOnlineUsersList.setBounds(626, 27, 94, 14);
		frmChameleonMessengerServer.getContentPane().add(lblOnlineUsersList);
		
		JMenuBar menuBar = new JMenuBar();
		frmChameleonMessengerServer.setJMenuBar(menuBar);
		
		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);
		
		JMenu mnHelp = new JMenu("Help");
		menuBar.add(mnHelp);
	}
	
	/**
	 * Prepare the server for a new start.
	 */
	@SuppressWarnings("unchecked")
	protected void resetServer() {
		btnStartServer.setEnabled(true);
		btnStopServer.setEnabled(false);
		listOfUsers.clear();
		clearServerMessages();
		
		JSONObject obj = new JSONObject();
		obj.put("command","DISCONNECT");
		String disconnect = obj.toJSONString();
		sendToAll(disconnect);
		
		try {
			loginSocket.close();
			userSocket.close();
		} catch (Exception ex) {
			addServerMessage("Couldn't close the server sockets!\nError: " + ex.getMessage());
		}
	}
	
	/**
	 * Adding message to serverMessagesArea
	 * 
	 * @param message: message to show
	 * 
	 */
	protected void addServerMessage(String message){
		serverMessagesArea.append(message + "\n");
		serverMessagesArea.setCaretPosition(serverMessagesArea.getDocument().getLength());
	}
	
	/**
	 * Clearing server messages
	 */
	protected void clearServerMessages() {
		serverMessagesArea.setText("");
		btnClearMessages.setEnabled(false);
	}
	
	/**
	 * Setting the Clear Server Messages button to enabled or disabled
	 */
	protected void checkServerMessages() {
		if ( serverMessagesArea.getText().equals("") ) {
			btnClearMessages.setEnabled(false);
		} else {
			btnClearMessages.setEnabled(true);
		}
	}
	
	private static void closeEvent() {
		System.out.println("The window was closed!");
	}

	public boolean getServerStatus() {
		return serverStatus;
	}

	public void setServerStatus(boolean serverStatus) {
		this.serverStatus = serverStatus;
	}
	
}
