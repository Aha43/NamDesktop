package swingdemo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads a JSON script of {@link DemoStep}s and replays them on the Swing EDT,
 * calling {@link RefreshBus#refresh()} after each step.
 *
 * <p>Usage:
 * <pre>
 *   var runner = new ScriptRunner(objectMapper, bus);
 *   runner.register("addProject", args -> service.addSubProject(...));
 *   runner.run(getClass().getResourceAsStream("/demo.json"));
 * </pre>
 *
 * <p>Must be started on the EDT ({@link SwingUtilities#invokeLater} or similar).
 * Each step's {@code delayMs} is the pause before that step fires.
 * Unrecognised action names are logged to stderr and skipped.
 */
public final class ScriptRunner {

    private final ObjectMapper               mapper;
    private final RefreshBus                 bus;
    private final Map<String, ActionHandler> handlers     = new HashMap<>();
    private Consumer<DemoStep>               onStep       = step -> {};
    private Runnable                         onComplete   = () -> {};
    private int                              failureCount = 0;

    public int getFailureCount() { return failureCount; }

    public ScriptRunner(ObjectMapper mapper, RefreshBus bus) {
        this.mapper = mapper;
        this.bus    = bus;
    }

    public ScriptRunner register(String action, ActionHandler handler) {
        handlers.put(action, handler);
        return this;
    }

    public ScriptRunner setOnStep(Consumer<DemoStep> listener) {
        this.onStep = listener != null ? listener : step -> {};
        return this;
    }

    public ScriptRunner setOnComplete(Runnable listener) {
        this.onComplete = listener != null ? listener : () -> {};
        return this;
    }

    public void run(InputStream json) throws IOException {
        var steps = mapper.readValue(json, new TypeReference<List<DemoStep>>() {});
        schedule(steps, 0);
    }

    private void schedule(List<DemoStep> steps, int index) {
        if (index >= steps.size()) {
            onComplete.run();
            return;
        }
        var step  = steps.get(index);
        int delay = step.delayMs() > 0 ? step.delayMs() : 1;
        var timer = new Timer(delay, e -> {
            onStep.accept(step);
            execute(step);
            bus.refresh();
            schedule(steps, index + 1);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void execute(DemoStep step) {
        var handler = handlers.get(step.action());
        if (handler == null) {
            System.err.println("[swingdemo] Unknown action: " + step.action());
            failureCount++;
            return;
        }
        try {
            handler.execute(step.args());
        } catch (Exception ex) {
            System.err.println("[swingdemo] Step failed (" + step.action() + "): " + ex.getMessage());
            failureCount++;
        }
    }
}
