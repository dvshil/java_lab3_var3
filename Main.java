import gui.RestaurantGUI;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }


        SwingUtilities.invokeLater(() -> {
            RestaurantGUI gui = new RestaurantGUI();
            gui.setLocationRelativeTo(null);
            gui.setVisible(true);
        });
    }
}
