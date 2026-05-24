package swingdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ScriptRunnerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static InputStream json(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void run_executesStepsInOrder() throws Exception {
        var executed = new ArrayList<String>();
        var latch    = new CountDownLatch(2);

        var runner = new ScriptRunner(MAPPER, () -> {})
                .register("stepA", args -> { executed.add("A"); latch.countDown(); })
                .register("stepB", args -> { executed.add("B"); latch.countDown(); });

        runner.run(json("""
                [
                  {"action":"stepA","delayMs":10},
                  {"action":"stepB","delayMs":10}
                ]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("A", "B"), executed);
    }

    @Test
    void run_passesArgsToHandler() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object>[] captured = new Map[1];
        var latch = new CountDownLatch(1);

        var runner = new ScriptRunner(MAPPER, () -> {})
                .register("greet", args -> { captured[0] = args; latch.countDown(); });

        runner.run(json("""
                [{"action":"greet","args":{"name":"Arne"},"delayMs":10}]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("Arne", captured[0].get("name"));
    }

    @Test
    void run_callsRefreshBusAfterEachStep() throws Exception {
        var refreshCount = new int[]{0};
        var latch        = new CountDownLatch(2);

        var runner = new ScriptRunner(MAPPER, () -> refreshCount[0]++)
                .register("noop", args -> latch.countDown());

        runner.run(json("""
                [
                  {"action":"noop","delayMs":10},
                  {"action":"noop","delayMs":10}
                ]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);
        assertEquals(2, refreshCount[0]);
    }

    @Test
    void run_skipsUnknownActionsWithoutCrashing() throws Exception {
        var latch = new CountDownLatch(1);

        var runner = new ScriptRunner(MAPPER, () -> {})
                .register("known", args -> latch.countDown());

        runner.run(json("""
                [
                  {"action":"unknown","delayMs":10},
                  {"action":"known",  "delayMs":10}
                ]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void run_continuesAfterHandlerException() throws Exception {
        var latch = new CountDownLatch(1);

        var runner = new ScriptRunner(MAPPER, () -> {})
                .register("boom",  args -> { throw new RuntimeException("oops"); })
                .register("after", args -> latch.countDown());

        runner.run(json("""
                [
                  {"action":"boom", "delayMs":10},
                  {"action":"after","delayMs":10}
                ]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void run_callsOnCompleteAfterLastStep() throws Exception {
        var latch = new CountDownLatch(1);

        var runner = new ScriptRunner(MAPPER, () -> {})
                .register("noop", args -> {})
                .setOnComplete(latch::countDown);

        runner.run(json("""
                [{"action":"noop","delayMs":10}]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void run_callsOnStepBeforeEachStep() throws Exception {
        var descriptions = new ArrayList<String>();
        var latch        = new CountDownLatch(2);

        var runner = new ScriptRunner(MAPPER, () -> {})
                .register("noop", args -> latch.countDown())
                .setOnStep(step -> descriptions.add(step.description()));

        runner.run(json("""
                [
                  {"action":"noop","delayMs":10,"description":"first"},
                  {"action":"noop","delayMs":10,"description":"second"}
                ]
                """));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);
        assertEquals(List.of("first", "second"), descriptions);
    }

    @Test
    void demoStep_defaultsArgsDelayAndDescriptionWhenAbsent() throws IOException {
        var step = MAPPER.readValue("""
                {"action":"x"}
                """, DemoStep.class);
        assertEquals("x", step.action());
        assertTrue(step.args().isEmpty());
        assertEquals(0, step.delayMs());
        assertEquals("", step.description());
    }
}
