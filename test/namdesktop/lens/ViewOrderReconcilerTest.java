package namdesktop.lens;

import namdesktop.model.NamNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewOrderReconcilerTest {

    private final ViewOrderReconciler reconciler = new ViewOrderReconciler();

    private static NamNode node(String title) {
        return new NamNode(UUID.randomUUID(), title);
    }

    @Test
    void reconcile_emptyOrder_returnsLiveItemsInOriginalOrder() {
        var a = node("A"); var b = node("B"); var c = node("C");
        var result = reconciler.reconcile(List.of(), List.of(a, b, c));
        assertEquals(List.of(a, b, c), result);
    }

    @Test
    void reconcile_savedOrderPreserved() {
        var a = node("A"); var b = node("B"); var c = node("C");
        var result = reconciler.reconcile(List.of(c.getId(), a.getId(), b.getId()), List.of(a, b, c));
        assertEquals(List.of(c, a, b), result);
    }

    @Test
    void reconcile_dropsItemsNoLongerLive() {
        var a = node("A"); var b = node("B"); var gone = node("Gone");
        var result = reconciler.reconcile(List.of(gone.getId(), a.getId(), b.getId()), List.of(a, b));
        assertEquals(List.of(a, b), result);
    }

    @Test
    void reconcile_appendsNewItemsInLiveOrder() {
        var a = node("A"); var newB = node("B"); var newC = node("C");
        var result = reconciler.reconcile(List.of(a.getId()), List.of(a, newB, newC));
        assertEquals(List.of(a, newB, newC), result);
    }

    @Test
    void reconcile_emptyLiveItems_returnsEmpty() {
        var gone = node("Gone");
        var result = reconciler.reconcile(List.of(gone.getId()), List.of());
        assertTrue(result.isEmpty());
    }
}
