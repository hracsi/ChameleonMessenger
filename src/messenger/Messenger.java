package messenger;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import data.Commands;

import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JSeparator;
import javax.swing.JList;
import javax.swing.border.EtchedBorder;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.Color;
import javax.swing.UIManager;

public class Messenger extends JFrame {
	
	private static final long serialVersionUID = 1708283679635395990L;
	private static final int DEFAULT_CHAT_PORT = 667;

	private JPanel contentPane;
	private JTextArea messageArea = new JTextArea();
	private JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
	private DefaultListModel usersList = new DefaultListModel();
	private JList onlineUsersList = new JList(usersList);
	
	private ArrayList<JScrollPane> tabScrollPanesForArea = new ArrayList<JScrollPane>();
	private ArrayList<JEditorPane> tabEditorPanes = new ArrayList<JEditorPane>();
	private ArrayList<String> tabMessages = new ArrayList<String>();
	private ArrayList<String> messagesUnderTab = new ArrayList<String>();
	private ArrayList<JPanel> tabTitles = new ArrayList<JPanel>();
	private static Integer tabCounter = 0;
	private static Integer prevTabIndex = 0;

	public Boolean serverIsOn = true;
	public Socket sock;
	public BufferedReader fromServer;
	public PrintWriter toServer;
	private User userInfo;

	public class InputReader implements Runnable {
		
		public void run() {
			String message;
			try {
				while ((message = fromServer.readLine()) != null ) {
					JSONParser parser = new JSONParser();
					JSONObject jsonObject = (JSONObject) parser.parse(message);

					String command = (String) jsonObject.get("command");
					Commands c = Commands.valueOf(command.toUpperCase());
					switch(c) {
						case MESSAGE:
							String name = (String) jsonObject.get("name");
							String visibility = (String) jsonObject.get("visibility");
							String msg = (String) jsonObject.get("message");
							try {
								addMessage(name, visibility, msg);
							} catch (Exception e) {
								System.out.println("ex: " + e.getMessage());
								e.printStackTrace();
							}
						break;
						case USERDATA:
							String userName = (String) jsonObject.get("name");
							userInfo.setUserName(userName);
							setTitle(userInfo.getUserName() + " - Chameleon Messenger");
						break;
						case USERSLIST:
							usersList.clear();
							JSONArray jsonUsersList = (JSONArray) jsonObject.get("users");
							
							@SuppressWarnings("unchecked")
							Iterator<String> jsonUserList = jsonUsersList.iterator();
							int i = 0;
							while (jsonUserList.hasNext()) {
								String partnerName = jsonUserList.next();
								if ( !userInfo.getUserName().equals(partnerName) ) {
									usersList.add(i, partnerName);
									i++;
								}
							}
						break;
						case DISCONNECT:
							addMessage("server", "", "Server has disconnected!");
							serverIsOn = false;
							JDialog dialog = new JDialog();
							JOptionPane.showMessageDialog(dialog, "The server is unexpectedly stopped!", "Server error", JOptionPane.WARNING_MESSAGE);
							dispose();
						break;
						default:
							throw new Exception("Unknown command!");
					}
				}
			} catch(Exception e) {
				System.out.println("Error while listening the sockets: " + e.getMessage());
			}
		}
	}
	
	/**
	 * Create the frame.
	 */
	public Messenger(String serverIP, String userId) {
		setResizable(false);
		setIconImage(Toolkit.getDefaultToolkit().getImage(Messenger.class.getResource("/messenger/icon.png")));
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setBounds(100, 100, 852, 612);
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		JMenu mnEdit = new JMenu("Edit");
		menuBar.add(mnEdit);
		
		JMenu mnHelp = new JMenu("Help");
		menuBar.add(mnHelp);
		
		contentPane = new JPanel();
		contentPane.setBackground(UIManager.getColor("Button.background"));
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		tabbedPane.setBackground(Color.LIGHT_GRAY);
		tabbedPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent arg0) {
				tabbedPane.getSelectedComponent();
			}
		});
		
		tabbedPane.setBounds(0, 0, 621, 453);
		contentPane.add(tabbedPane);

		newTab("Public");
		
		tabbedPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				int index = tabbedPane.getSelectedIndex();
				messagesUnderTab.set(prevTabIndex, messageArea.getText());
				messageArea.setText(messagesUnderTab.get(index));
				prevTabIndex = index;
			}
		});
		
		JScrollPane scrollPaneMessage = new JScrollPane();
		scrollPaneMessage.setBounds(0, 464, 621, 48);
		contentPane.add(scrollPaneMessage);
		messageArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if ( e.getKeyCode() == KeyEvent.VK_ENTER && !messageArea.getText().equals("")) {
					sendMessage();
				}
			}
			
			public void keyReleased(KeyEvent e) {
				messagesUnderTab.set(tabbedPane.getSelectedIndex(), messageArea.getText());
				if ( e.getKeyCode() == KeyEvent.VK_ENTER && !messageArea.getText().equals("")) {
					messageArea.setText("");
				}
			}
		});
		messageArea.setFont(new Font("Tahoma", Font.PLAIN, 13));
		
		scrollPaneMessage.setViewportView(messageArea);
		
		JSeparator separator = new JSeparator();
		separator.setBounds(0, 458, 621, 2);
		contentPane.add(separator);
		
		onlineUsersList.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		onlineUsersList.setBounds(624, 21, 222, 491);
		contentPane.add(onlineUsersList);
		
		onlineUsersList.addMouseListener(new MouseAdapter() {
		    public void mouseClicked(MouseEvent evt) {
		        JList list = (JList) evt.getSource();
		        if (evt.getClickCount() == 2) {
		        	int index = list.locationToIndex(evt.getPoint());
		        	Boolean canOpen = true;
		        	int i = 0;
		        	Iterator<JPanel> it = tabTitles.iterator();
		        	while (it.hasNext()) {
		        		JLabel lbl = (JLabel) it.next().getComponent(0);
						if ( lbl.getText().equals(list.getModel().getElementAt(index)) ) {
							System.out.print(lbl.getText());
							canOpen = false;
							break;
						}
						i++;
					}
		            
		        	if ( canOpen ) {
		        		newTab((String) list.getModel().getElementAt(index));
		        	} else {
		        		tabbedPane.setSelectedIndex(i);
		        	}
		        }
		    }
		});
		
		JLabel lblOnlineUsers = new JLabel("Online users:");
		lblOnlineUsers.setBounds(624, 4, 100, 14);
		contentPane.add(lblOnlineUsers);
		
		init(serverIP, userId);
		messageArea.requestFocus();
	}
	
	private void newTab(String title) {
		//tab close button
		JButton tabCloseButton = new JButton(new ImageIcon(getClass().getResource("cancel.png")));
		tabCloseButton.setBorder(BorderFactory.createEmptyBorder());
		tabCloseButton.setActionCommand("" + tabCounter);
		tabCloseButton.setToolTipText("Close chat with " + title);		

		tabCloseButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JButton actBtn = (JButton) e.getSource();
				JButton btn;
				for (int i = 1; i < tabbedPane.getTabCount(); i++) {
					JPanel pnl = (JPanel) tabbedPane.getTabComponentAt(i);
					btn = (JButton) pnl.getComponent(1);
					if (actBtn.getActionCommand().equals(btn.getActionCommand())) {
						tabTitles.remove(i);
						tabEditorPanes.remove(i);
						tabMessages.remove(i);
						tabScrollPanesForArea.remove(i);
						tabbedPane.removeTabAt(i);
						tabCounter--;
						break;
					}
        		}
			}
		});

		//tab label
		JLabel lbl = new JLabel(title);
		lbl.setIconTextGap(5);
		lbl.setHorizontalTextPosition(SwingConstants.LEFT);

		//tab label and button on one panel
		JPanel pnl = new JPanel();
		pnl.setOpaque(true);
		pnl.setBackground(Color.WHITE);
		pnl.add(lbl);
		if (tabCounter != 0) {
			pnl.add(tabCloseButton);
		}
		tabTitles.add(tabCounter, pnl);
		
		//tab content
		JEditorPane editorPane = new JEditorPane();
		editorPane.setEditable(false);
		editorPane.setContentType("text/html");
		tabEditorPanes.add(tabCounter, editorPane);
		
		//tab scroll
		JScrollPane scrollPane = new JScrollPane(tabEditorPanes.get(tabCounter), ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setAutoscrolls(true);
		scrollPane.setViewportView(tabEditorPanes.get(tabCounter));
		tabScrollPanesForArea.add(tabCounter, scrollPane);
		
		//adding tab
		tabbedPane.addTab("", null, tabScrollPanesForArea.get(tabCounter), "Click to chat with " + title);
		tabbedPane.setTabComponentAt(tabbedPane.getTabCount()-1, pnl);
		
		//set null message under the tab and in the content of the tab
		tabMessages.add(tabCounter, "");
		messagesUnderTab.add(tabCounter, "");
		
		//increment the tabCounter
		tabCounter++;
	}
	
	@SuppressWarnings("unchecked")
	private void sendMessage() {
		try {
			//getting the actual tab index
			int index = tabbedPane.getSelectedIndex(); 
			
			//Getting tab title
			JLabel lbl = (JLabel) tabTitles.get(index).getComponent(0);
		
			//create JSON Object for sending
			JSONObject message = new JSONObject();
			message.put("command", "message");
			message.put("name", userInfo.getUserName());
			message.put("visibility", lbl.getText());
			
			message.put("message", messageArea.getText());
			String json = message.toJSONString();  

			//sending JSON data to server
			toServer.print(json + "\n");
			toServer.flush();
			
			//set the message under the tab to null
			messagesUnderTab.set(index, "");
			messageArea.setText("");
		} catch (Exception e) {
			System.out.println("Error while sending message!\n" + e.getMessage());
		}
	}
	
	@SuppressWarnings("unchecked")
	private void init(String serverIP, String userId) {
		try {
			sock 		= new Socket(serverIP, DEFAULT_CHAT_PORT);			
			fromServer	= new BufferedReader(new InputStreamReader(sock.getInputStream()));
			toServer	= new PrintWriter(sock.getOutputStream(), true);
			
			Thread InputReader = new Thread(new InputReader());
			InputReader.start();

			userInfo = new User();
			userInfo.setUserId(userId);
			
			JSONObject newUser = new JSONObject();
			newUser.put("command", "NEWUSER");
			newUser.put("id", userId);
			String json = newUser.toJSONString();  
			
			toServer.print(json + "\n");
			toServer.flush();
		} catch (IOException e) {
			System.out.println("Couldn't create the chat socket\n");
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	private void getUserData() {
		JSONObject newUser = new JSONObject();
		newUser.put("command", "userData");
		newUser.put("id", userInfo.getUserId());
		String json = newUser.toJSONString();
		
		toServer.print(json + "\n");
		toServer.flush();
	}
	
	@SuppressWarnings("unchecked")
	public void disconnect() {
		JSONObject newUser = new JSONObject();
		newUser.put("command", "disconnect");
		newUser.put("id", userInfo.getUserId());
		String json = newUser.toJSONString();
		
		toServer.print(json + "\n");
		toServer.flush();
	}

	private void addMessage(String name, String visibility, String message) throws Exception {
		//replace smileys
		message = replaceWithSmiley(message);
		if ( visibility.toLowerCase().equals("public") ) {			//if the message is for everyone
			if ( name.equals(userInfo.getUserName())) {					//if the message sent by the current user
				tabMessages.set(0, tabMessages.get(0) + "<b><font color=\"green\">" + name + ":</font></b><br />" + message + "<br/><br/>\n\n");
			} else {
				tabMessages.set(0, tabMessages.get(0) + "<b><font color=\"blue\">" + name + ":</font></b><br />" + message + "<br/><br/>\n\n");
			}
			tabEditorPanes.get(0).setText(tabMessages.get(0));
			tabEditorPanes.get(0).setCaretPosition(tabEditorPanes.get(0).getDocument().getLength());
		} else if ( visibility.toLowerCase().equals("server") ) {	//if the message is a server message
			tabMessages.set(0, tabMessages.get(0) + "<b><font color=\"red\">Server message:</font></b><br />" + message + "<br/><br/>\n\n");
			tabEditorPanes.get(0).setText(tabMessages.get(0));
			tabEditorPanes.get(0).setCaretPosition(tabEditorPanes.get(0).getDocument().getLength());
		} else if ( visibility.equals(userInfo.getUserName()) || name.equals(userInfo.getUserName()) ) {	//if it's a private message and the user can see it
			String compare = name;
			String color = "green";
			int index;
			if ( name.equals(userInfo.getUserName()) ) {			//if the current user sent the private message
				compare = visibility;
				color = "blue";
			}
			
			boolean isOpened = false;
			int i = 0;
        	Iterator<JPanel> it = tabTitles.iterator();
        	while (it.hasNext()) {
        		JLabel lbl = (JLabel) it.next().getComponent(0);
				if ( lbl.getText().equals(compare) ) {
					isOpened = true;
					break;
				}
				i++;
			}
			
        	if ( isOpened ) {
        		index = i;
        	} else {
        		index = tabCounter;
        		newTab(compare);
        	}
        	tabMessages.set(index, tabMessages.get(index) + "<b><font color=\"" + color + "\">" + name + ":</font></b><br />" + message + "<br/><br/>\n\n");
        	tabEditorPanes.get(index).setText(tabMessages.get(index));
        	tabEditorPanes.get(index).setCaretPosition(tabEditorPanes.get(index).getDocument().getLength());
		} else {													//invalid type of visibility
			throw new Exception("Invalid visibility! Visibility: " + visibility);
		}
		//messages.setCaretPosition(messages.getDocument().getLength());
	}
	
	private String replaceWithSmiley(final String message) {
		String pathToSmileys = "messenger/";
		Messenger.class.getClassLoader();
		HashMap<String, String> smileys = new HashMap<String, String>();
		
		String smile = ClassLoader.getSystemResource(pathToSmileys + "smiley_smile.png").toString();
		String sad = ClassLoader.getSystemResource(pathToSmileys + "smiley_sad.png").toString();
		String laugh = ClassLoader.getSystemResource(pathToSmileys + "smiley_laugh.png").toString();
		String bad = ClassLoader.getSystemResource(pathToSmileys + "smiley_bad.png").toString();
		String wink = ClassLoader.getSystemResource(pathToSmileys + "smiley_wink.png").toString();
		String devil = ClassLoader.getSystemResource(pathToSmileys + "smiley_devil.png").toString();
		String angel = ClassLoader.getSystemResource(pathToSmileys + "smiley_angel.png").toString();

		smileys.put(":\\)", smile);
		smileys.put(":\\(", sad);
		smileys.put(":D", laugh);
		smileys.put(" :/", bad);
		smileys.put(";\\)", wink);
		smileys.put(">\\)", devil);
		smileys.put("\\(A\\)", angel);
				
		String messageWithSmileys = message;
		for(Entry<String, String> smiley : smileys.entrySet()) {
			messageWithSmileys = messageWithSmileys.replaceAll(smiley.getKey(), " <img src=\"" + smiley.getValue() + "\" height=\"16\" width=\"16\" />");
		}

		return messageWithSmileys;
	}

}
