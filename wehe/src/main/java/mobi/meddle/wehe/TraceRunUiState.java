package mobi.meddle.wehe;

import java.util.HashMap;
import java.util.Map;

public class TraceRunUiState {
    public enum StateType {
        STATUS_UPDATE,
        PROGRESS_UPDATE,
        PROGRESS_COMPLETE,
        SHOW_TOAST,
        SHOW_DIALOG
    }

    private final StateType type;
    private final Map<String, Object> data;

    private TraceRunUiState(StateType type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public StateType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    // Factory methods for different states
    public static TraceRunUiState createStatusUpdate(String appName, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("appName", appName);
        data.put("status", status);
        return new TraceRunUiState(StateType.STATUS_UPDATE, data);
    }

    public static TraceRunUiState createProgressUpdate(int progress) {
        Map<String, Object> data = new HashMap<>();
        data.put("progress", progress);
        return new TraceRunUiState(StateType.PROGRESS_UPDATE, data);
    }

    public static TraceRunUiState createProgressComplete(int iteration) {
        Map<String, Object> data = new HashMap<>();
        data.put("iteration", iteration);
        return new TraceRunUiState(StateType.PROGRESS_COMPLETE, data);
    }

    public static TraceRunUiState createShowToast(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return new TraceRunUiState(StateType.SHOW_TOAST, data);
    }

    public static TraceRunUiState createShowDialog(String title, String message, boolean exitReplays) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("message", message);
        data.put("exitReplays", exitReplays);
        return new TraceRunUiState(StateType.SHOW_DIALOG, data);
    }
}
