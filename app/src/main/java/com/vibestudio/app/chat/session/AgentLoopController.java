package com.vibestudio.app.chat.session;

import com.vibestudio.app.service.LogViewerService;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Drives the model -> tool -> model cycle for one user request. It owns the loop state and the turn budget;
 * what happens inside a single turn is supplied as a {@link Step}.
 */
public class AgentLoopController {

    public enum State {
        /** Waiting for user input. */
        IDLE,
        /** Payload sent to the model; waiting for a tool call or a final answer. */
        AWAITING_MODEL_TURN,
        /** A tool requested by the model is running. */
        EXECUTING_TOOL,
        /** Tool output is in the history; the next model turn starts automatically. */
        STEP_COMPLETED,
        /** The model answered without a tool call, or the run was stopped, failed or ran out of turns. */
        TASK_FINISHED
    }

    public enum StepResult {
        /** A tool ran and its output was appended to the history: take another model turn. */
        CONTINUE,
        /** Nothing left to do for this request. */
        FINISHED
    }

    public interface Step {
        StepResult run(AgentLoopController loop);
    }

    public static final int DEFAULT_MAX_TURNS = 50;

    /** Upper bound on tool calls run from one model reply when the model supports parallel calls. */
    public static final int MAX_PARALLEL_TOOL_CALLS = 8;

    /** Appended to the system instruction so the model keeps calling tools until the task is done. */
    public static final String STOP_GUARD = "\n=== AGENT LOOP RULES ===\n"
            + "When executing a multi-step task, EVERY response MUST contain a tool call until the task is complete. "
            + "Never output conversational updates alone while work remains. "
            + "When the task is finished, or you need input from the user, call the 'task_complete' tool "
            + "(text-protocol form: {\"tool\": \"task_complete\", \"args\": {\"summary\": \"...\"}}). "
            + "A response without a tool call is treated as an unfinished turn.\n";

    public static final String NUDGE_MESSAGE = "Continue: if the task is not finished, call the next tool now. "
            + "If it is finished, or you need input from the user, call 'task_complete'.";

    public static final String TERMINAL_NUDGE_MESSAGE = "A terminal command may still be running. Do not reply with text: "
            + "call 'read_terminal_output' (set planned_delay_ms to wait for progress) to check it, and keep working "
            + "until the task is done. When it is finished, or you need input from the user, call 'task_complete'.";

    /** The nudge to send after a text-only reply, given the last tool the model called. */
    public static String nudgeFor(String lastToolName) {
        if ("execute_command_async".equals(lastToolName) || "read_terminal_output".equals(lastToolName)
                || "send_keystroke".equals(lastToolName)) {
            return TERMINAL_NUDGE_MESSAGE;
        }
        return NUDGE_MESSAGE;
    }

    private volatile State mState = State.IDLE;
    private volatile int mMaxTurns = DEFAULT_MAX_TURNS;
    private volatile int mTurn;

    public State getState() {
        return mState;
    }

    /** Called by a step to mark phases such as {@link State#EXECUTING_TOOL}. */
    public void setState(State state) {
        mState = state;
    }

    public int getTurn() {
        return mTurn;
    }

    public int getMaxTurns() {
        return mMaxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        mMaxTurns = Math.max(1, maxTurns);
    }

    /**
     * Runs turns until the step reports {@link StepResult#FINISHED}, stop is requested, the turn budget is
     * exhausted ({@code onMaxTurnsReached}) or a step throws ({@code onError}). Blocks the calling thread.
     */
    public void run(Step step, BooleanSupplier stopRequested, Runnable onMaxTurnsReached, Consumer<Throwable> onError) {
        mTurn = 0;
        try {
            while (true) {
                if (mTurn >= mMaxTurns) {
                    LogViewerService.getInstance().w("AgentLoopController", "Max turns reached: " + mMaxTurns);
                    onMaxTurnsReached.run();
                    return;
                }
                mTurn++;
                mState = State.AWAITING_MODEL_TURN;
                if (step.run(this) == StepResult.FINISHED || stopRequested.getAsBoolean()) {
                    return;
                }
                mState = State.STEP_COMPLETED;
            }
        } catch (RuntimeException e) {
            LogViewerService.getInstance().e("AgentLoopController", "Agent turn failed: " + e.getMessage(), e);
            onError.accept(e);
        } finally {
            mState = State.TASK_FINISHED;
        }
    }
}
