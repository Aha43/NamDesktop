package namdesktop.ui;

import javax.swing.*;
import java.awt.*;

public final class MainFrame extends JFrame {

    public MainFrame() {
        var leftPanel = new JPanel();
        var centrePanel = new JPanel();

        var splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, centrePanel);
        splitPane.setDividerLocation(220);
        splitPane.setResizeWeight(0.0);

        add(splitPane, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
    }
}
