package messenger;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URL;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import data.Commands;

import java.awt.Dimension;

public class Login extends JFrame {

	private static final long serialVersionUID = 2174997086242625023L;
	private static final int DEFAULT_LOGIN_PORT = 666;

	
	private Socket sock;
	public BufferedReader reader;
	private PrintWriter writer;
    private volatile boolean isConnected = false;
	
    private static Login frame;
	private JPanel contentPane;
	private JTextField serverIPField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JLabel lblPassword;
    private JButton btnSignIn = new JButton("Sign in");
    private JLabel lblRegistration;
    
    public class InputReader implements Runnable {
	
		public void run() {
			if ( isConnected && sock.isConnected() ) {
				String message;
				try {
					while ((message = reader.readLine()) != null ) {
						JSONParser parser = new JSONParser();
						JSONObject jsonObject = (JSONObject) parser.parse(message);
	
						String command = (String) jsonObject.get("command");
						Commands c = Commands.valueOf(command.toUpperCase());
						switch(c) {
							case VERIFYLOGIN:
								String verify = (String) jsonObject.get("verify");
								if ( verify.equals("valid") ) {
										String id = (String) jsonObject.get("id");
										openMessenger(id);
								} else if ( verify.equals("alreadyLoggedIn") ) {
									String name = (String) jsonObject.get("name");
									JOptionPane.showMessageDialog(frame, name +" is already logged in!", "Login error", JOptionPane.ERROR_MESSAGE);
									btnSignIn.setEnabled(true);
									serverIPField.setEditable(true);
									emailField.setEditable(true);
									passwordField.setEditable(true);
								} else {
									btnSignIn.setEnabled(true);
									serverIPField.setEditable(true);
									emailField.setEditable(true);
									passwordField.setEditable(true);
									JOptionPane.showMessageDialog(frame, "Invalid username or password!", "Login error", JOptionPane.ERROR_MESSAGE);
								}
							break;
							default:
								System.out.println("Default: " + message);
							break;
						}
					}
				} catch(Exception e) {
					System.out.println(e.getMessage());
				}
			}
		}
	}
    
    private void openMessenger(final String id) {
		frame.setVisible(false);
		closeSocket(); 

		final Messenger msgr;
		try {
		
			msgr = new Messenger(serverIPField.getText(), id);
			msgr.setVisible(true);
			msgr.addWindowListener( new WindowAdapter()
			{
			    public void windowClosing(WindowEvent e)
			    {
			        JFrame dialog = (JFrame) e.getSource();
	
			        int result = JOptionPane.showConfirmDialog(dialog, "Are you sure you want to exit the messenger?", "Exit Application", JOptionPane.YES_NO_OPTION);
			        if (result == JOptionPane.YES_OPTION) {
			        	dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			        	try {
			        		msgr.disconnect();
			    			msgr.toServer.close();
			    			msgr.fromServer.close();
			    			msgr.sock.close();
			    		} catch (Exception ex) {
			    			System.out.println("Couldn't close the streams and the sockets!");
			    		}
			        	
			            frame.setVisible(true);
						btnSignIn.setEnabled(true);
						serverIPField.setEditable(true);
						emailField.setEditable(true);
						passwordField.setEditable(true);
			        }
			    }
			});
			} catch (Exception e) {
				e.printStackTrace();
			}
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			//Set System L&F
	        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	    } catch (Exception e) {
	    	System.out.println("Couldn't set the look!");
	    	e.printStackTrace();
	    }
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					frame = new Login();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public Login() {
		setResizable(false);
		setMaximumSize(new Dimension(750, 550));
		setMinimumSize(new Dimension(750, 550));
		setIconImage(Toolkit.getDefaultToolkit().getImage(Login.class.getResource("/messenger/icon.png")));

		setTitle("Chameleon Messenger Login");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 750, 557);
		contentPane = new JPanel();
		contentPane.setBackground(SystemColor.textText);
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JLabel lblNewLabel = new JLabel("Server IP:");
		lblNewLabel.setFont(new Font("Times New Roman", Font.PLAIN, 14));
		lblNewLabel.setForeground(new Color(0, 153, 0));
		lblNewLabel.setBounds(346, 278, 77, 14);
		contentPane.add(lblNewLabel);
		
		serverIPField = new JTextField();
		serverIPField.setText("127.0.0.1");
		serverIPField.setBounds(346, 305, 62, 20);
		contentPane.add(serverIPField);
		serverIPField.setColumns(10);
		
		btnSignIn.addActionListener(new ActionListener() {
			@SuppressWarnings("unchecked")
			public void actionPerformed(ActionEvent e) {
				if (isConnected == false) {
					openSocket();
				}
				//btnSignIn.setEnabled(false);
				serverIPField.setEditable(false);
				emailField.setEditable(false);
				//passwordField.setEditable(false);
				try {
					JSONObject login = new JSONObject();
					login.put("command", "login");
					login.put("email", emailField.getText());
					login.put("password", new String(passwordField.getPassword()));
					String json = login.toJSONString();  

					writer.print(json + "\n");
					writer.flush();
				} catch (Exception ex) {
					btnSignIn.setEnabled(true);
					serverIPField.setEditable(true);
					emailField.setEditable(true);
					passwordField.setEditable(true);
					JOptionPane.showMessageDialog(frame, "Couldn't connect to the server!", "Connection error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});
		btnSignIn.setBounds(334, 453, 89, 23);
		contentPane.add(btnSignIn);
		
		emailField = new JTextField();
		emailField.setText("hracsi@gmail.com");
		emailField.setBounds(281, 360, 195, 20);
		contentPane.add(emailField);
		emailField.setColumns(10);
		
		passwordField = new JPasswordField();
		passwordField.setBounds(281, 417, 195, 20);
		contentPane.add(passwordField);
		
		JLabel lblNewLabel_1 = new JLabel("E-mail:");
		lblNewLabel_1.setFont(new Font("Times New Roman", Font.PLAIN, 14));
		lblNewLabel_1.setForeground(new Color(255, 204, 0));
		lblNewLabel_1.setBounds(281, 335, 142, 14);
		contentPane.add(lblNewLabel_1);
		
		//Creating header image
		Header header = new Header();
		header.setBackground(Color.BLACK);
		header.setBounds(0, 0, 750, 280);
		contentPane.add(header);
		
		lblPassword = new JLabel("Password:");
		lblPassword.setFont(new Font("Times New Roman", Font.PLAIN, 14));
		lblPassword.setForeground(new Color(255, 204, 0));
		lblPassword.setBounds(281, 392, 142, 14);
		contentPane.add(lblPassword);
		
		lblRegistration = new JLabel("Registration");
		lblRegistration.setToolTipText("Click here to create an account for Chameleon Messenger!");
		lblRegistration.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
				URL url;
				URI uri = null;
				try {
					url = new URL("http://messenger.hracsi.net/registration");
					uri = url.toURI();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
			        try {
			            desktop.browse(uri);
			        } catch (Exception ex) {
			            ex.printStackTrace();
			        }
			    }
			}
		});
		lblRegistration.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		lblRegistration.setFont(new Font("Times New Roman", Font.PLAIN, 13));
		lblRegistration.setForeground(new Color(0, 153, 255));
		lblRegistration.setBounds(663, 488, 71, 20);
		contentPane.add(lblRegistration);
		
		JLabel lblChameleonMessengerV = new JLabel("Chameleon messenger v. 1.0 \u00DFeta");
		lblChameleonMessengerV.setForeground(Color.LIGHT_GRAY);
		lblChameleonMessengerV.setBounds(10, 491, 181, 14);
		contentPane.add(lblChameleonMessengerV);
	}
	
	private void openSocket() {
		if ( isConnected ) {
			return;
		}
			
		try {
			sock = new Socket(serverIPField.getText(), DEFAULT_LOGIN_PORT);
			reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			writer = new PrintWriter(sock.getOutputStream(), true);
			isConnected = true;
			
			Thread InputReader = new Thread(new InputReader());
			InputReader.start();
		} catch ( Exception e) {
			System.out.println("Couldn't create to socket!\n");
		}
	}
	
	private void closeSocket() {
		if ( !isConnected ) {
			return;
		}
    	try {
			reader.close();
			writer.close();
			sock.close();
			isConnected = false;
		} catch (Exception ex) {
			System.out.print("Couldn't close the streams and the sockets!");
		}		
	}
}

