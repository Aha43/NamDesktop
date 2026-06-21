package namdesktop.ui;

import namdesktop.service.NamWorkspaceService;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Wires a table's trailing checkbox column for bulk selection (#402): fixed narrow width, a
 * select/clear-all header checkbox, commit-on-focus-loss, and a {@link BulkActionBar} that the
 * caller adds to its layout. Keeps each panel's integration to a model tweak + one call.
 */
final class BulkSelect {

    private BulkSelect() {}

    /**
     * @param checkCol      the checkbox column index
     * @param check         the panel's selection state (lives on its table model)
     * @param selectable    count of selectable rows (for the header's all-checked state)
     * @param selectableIds ids of all selectable rows (header toggle target)
     * @param refresh       rebuilds the panel after a bulk action
     * @return the bar to add to the layout (hidden until something is checked)
     */
    static BulkActionBar install(JTable table, int checkCol, CheckColumn check,
                                 IntSupplier selectable, Supplier<List<UUID>> selectableIds,
                                 NamWorkspaceService service, Runnable refresh) {
        // Look up by MODEL index, not view index — some panels hide columns (e.g. the Status column),
        // so the checkbox's view position differs from its model column.
        var col = columnForModelIndex(table, checkCol);
        col.setPreferredWidth(28);
        col.setMaxWidth(28);
        col.setHeaderRenderer((t, v, sel, foc, r, c) -> {
            var box = new JCheckBox();
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setSelected(check.allChecked(selectable.getAsInt()));
            box.setToolTipText("Select / clear all");
            return box;
        });
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                var view = table.columnAtPoint(e.getPoint());
                if (view >= 0 && table.convertColumnIndexToModel(view) == checkCol) {
                    check.setAll(selectableIds.get(), !check.allChecked(selectable.getAsInt()));
                    table.getTableHeader().repaint();
                }
            }
        });
        var bar = new BulkActionBar(service, check::ids, () -> { check.clear(); refresh.run(); });
        check.setOnChange(() -> { bar.setCount(check.count()); table.getTableHeader().repaint(); });
        return bar;
    }

    private static TableColumn columnForModelIndex(JTable table, int modelIndex) {
        var cm = table.getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++)
            if (cm.getColumn(i).getModelIndex() == modelIndex) return cm.getColumn(i);
        throw new IllegalStateException("No column with model index " + modelIndex);
    }
}

