package swingdemo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * A single step in a demo script.
 *
 * JSON shape:
 * <pre>
 * { "action": "addProject", "args": { "title": "Trip to Rome" }, "delayMs": 500 }
 * </pre>
 *
 * delayMs is the pause before this step executes (default 0).
 */
public final class DemoStep {

    private final String              action;
    private final Map<String, Object> args;
    private final int                 delayMs;
    private final String              description;

    @JsonCreator
    public DemoStep(
            @JsonProperty("action")      String              action,
            @JsonProperty("args")        Map<String, Object> args,
            @JsonProperty("delayMs")     int                 delayMs,
            @JsonProperty("description") String              description) {
        this.action      = action      != null ? action      : "";
        this.args        = args        != null ? args        : Map.of();
        this.delayMs     = Math.max(0, delayMs);
        this.description = description != null ? description : "";
    }

    public String              action()      { return action; }
    public Map<String, Object> args()        { return args; }
    public int                 delayMs()     { return delayMs; }
    public String              description() { return description; }

    @Override public String toString() {
        return "DemoStep[action=" + action + ", delayMs=" + delayMs + "]";
    }
}
