package namdesktop.ui;

import javax.swing.*;
import java.awt.*;

public final class ContentArea extends JPanel {

    public ContentArea() {
        setLayout(new BorderLayout());
    }

    public JComponent getContent() {
        return getComponentCount() > 0 ? (JComponent) getComponent(0) : null;
    }

    public void setContent(JComponent content) {
        removeAll();
        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
