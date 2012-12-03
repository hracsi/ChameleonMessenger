package messenger;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class Header extends JPanel {

	private static final long serialVersionUID = 917891194215619723L;
	private BufferedImage image;

    public Header() {
       try {                
          image = ImageIO.read(new File("header.jpg"));
       } catch (Exception e) {
    	   System.out.print("Couldn't load the image!\n" + e.getMessage() + "\n" + e.getCause());
       }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, null);            
    }

}