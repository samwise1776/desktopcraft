package tubeforge;

import javax.swing.*;

public final class TubeForge {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            new TubeForgeFrame().setVisible(true);
        });
    }
}
