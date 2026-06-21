package namdesktop.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;

/**
 * Central look-and-feel setup plus two desktop-only readability tweaks (#398):
 *
 * <ul>
 *   <li><b>Theme-adaptive icons.</b> The SVG icons use {@code stroke="currentColor"}, which renders
 *       as pitch black — fine in light mode, but nearly invisible on the dark panels. A global
 *       {@link FlatSVGIcon.ColorFilter} remaps pure black to a light gray in dark themes (and a
 *       near-black in light themes), so icons stay legible in both.</li>
 *   <li><b>Adjustable background brightness.</b> An optional "lift" lightens the panel/surface grays
 *       by a percentage toward white, for users who want a softer (less dark) theme.</li>
 * </ul>
 *
 * Use {@link #apply(Theme, int)} everywhere instead of calling {@code FlatXLaf.setup()} directly.
 */
public final class UiTheme {

    /** Maximum background lift, in percent toward white. */
    public static final int MAX_BG_LIFT = 20;

    /** Icon stroke color: follow the theme, or force light/dark regardless of theme. */
    public enum IconColor { AUTO, LIGHT, DARK }

    private static final Color ICON_LIGHT = new Color(0xCF, 0xCF, 0xCF);
    private static final Color ICON_DARK  = new Color(0x1E, 0x1E, 0x1E);

    private UiTheme() {}

    // Surface backgrounds lifted together so the grayscale stays consistent across components.
    private static final String[] BG_KEYS = {
            "Panel.background", "control", "ToolBar.background", "MenuBar.background",
            "Menu.background", "MenuItem.background", "PopupMenu.background",
            "TabbedPane.background", "Table.background", "TableHeader.background",
            "Tree.background", "List.background", "ScrollPane.background", "Viewport.background",
            "SplitPane.background", "OptionPane.background", "TitlePane.background",
            "TextField.background", "FormattedTextField.background", "PasswordField.background",
            "TextArea.background", "EditorPane.background", "TextPane.background",
            "ComboBox.background", "Spinner.background", "ScrollBar.background", "ToggleButton.background"
    };

    /** Installs the look-and-feel for {@code theme}, plus the icon recolor and background lift. */
    public static void apply(Theme theme, int bgLiftPercent, IconColor iconColor) {
        if (theme == Theme.LIGHT) FlatLightLaf.setup(); else FlatDarkLaf.setup();
        installIconColors(iconColor != null ? iconColor : IconColor.AUTO);
        liftBackgrounds(clampLift(bgLiftPercent));
    }

    public static int clampLift(int pct) {
        return Math.max(0, Math.min(MAX_BG_LIFT, pct));
    }

    // Map pitch-black icon strokes. The global filter applies the first target under light themes and
    // the second under dark themes; forcing LIGHT/DARK uses the same color for both. Re-adding the
    // same source color overwrites the previous mapping, so this is safe to call on every apply.
    private static void installIconColors(IconColor mode) {
        var filter = FlatSVGIcon.ColorFilter.getInstance();
        switch (mode) {
            case AUTO  -> filter.add(Color.BLACK, ICON_DARK,  ICON_LIGHT); // dark in light theme, light in dark
            case LIGHT -> filter.add(Color.BLACK, ICON_LIGHT, ICON_LIGHT); // light icons always
            case DARK  -> filter.add(Color.BLACK, ICON_DARK,  ICON_DARK);  // dark icons always
        }
    }

    private static void liftBackgrounds(int pct) {
        // Read the pristine base from the freshly-installed look-and-feel defaults — NOT from
        // UIManager.getColor(), whose value carries our own override from the previous apply and
        // would make the lift compound (and never reset when sliding back). Re-apply on every call,
        // including pct == 0, so the override always reflects the current slider value exactly.
        var laf = UIManager.getLookAndFeelDefaults();
        for (var key : BG_KEYS) {
            var base = laf.getColor(key);
            if (base == null) continue;
            var c = pct > 0 ? lighten(base, pct) : base;
            UIManager.put(key, new ColorUIResource(c));
        }
    }

    private static Color lighten(Color c, int pct) {
        double f = pct / 100.0;
        return new Color(
                (int) Math.round(c.getRed()   + (255 - c.getRed())   * f),
                (int) Math.round(c.getGreen() + (255 - c.getGreen()) * f),
                (int) Math.round(c.getBlue()  + (255 - c.getBlue())  * f));
    }
}
