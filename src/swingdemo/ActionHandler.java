package swingdemo;

import java.util.Map;

/**
 * Handles a single named action in a demo script.
 * Implementations are registered with ScriptRunner and are app-specific.
 */
public interface ActionHandler {
    void execute(Map<String, Object> args) throws Exception;
}
