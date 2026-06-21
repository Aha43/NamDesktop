package namdesktop.ui;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Selection state for a table's bulk-action checkbox column (#402). Holds the set of checked node
 * ids and notifies on change; a table model delegates its checkbox cell to this, and the panel reads
 * {@link #ids()} for bulk operations. Reused across the list panels and the Project Workbench.
 */
final class CheckColumn {

    private final LinkedHashSet<UUID> checked = new LinkedHashSet<>();
    private Runnable onChange = () -> {};

    void setOnChange(Runnable r) { this.onChange = r != null ? r : () -> {}; }

    boolean isChecked(UUID id) { return checked.contains(id); }

    void set(UUID id, boolean on) {
        if (on ? checked.add(id) : checked.remove(id)) onChange.run();
    }

    /** Replace the whole selection: check {@code ids} when {@code on}, else clear. */
    void setAll(Collection<UUID> ids, boolean on) {
        checked.clear();
        if (on) checked.addAll(ids);
        onChange.run();
    }

    /** Drop checks for ids no longer present (call on refresh). */
    void retain(Collection<UUID> live) {
        if (checked.retainAll(live)) onChange.run();
    }

    void clear() {
        if (!checked.isEmpty()) { checked.clear(); onChange.run(); }
    }

    List<UUID> ids() { return List.copyOf(checked); }
    int     count() { return checked.size(); }
    boolean allChecked(int rowCount) { return rowCount > 0 && checked.size() == rowCount; }
}
