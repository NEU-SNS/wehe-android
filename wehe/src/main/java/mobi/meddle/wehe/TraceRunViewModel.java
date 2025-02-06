package mobi.meddle.wehe;

import android.content.DialogInterface;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mobi.meddle.wehe.bean.ApplicationBean;

public class TraceRunViewModel extends ViewModel {
    private final MutableLiveData<TraceRunUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> progressBarVisible = new MutableLiveData<>(false);
    private final TraceRunRepository traceRunRepository;
    private final ExecutorService executorService;

    public TraceRunViewModel(TraceRunRepository traceRunRepository) {
        this.traceRunRepository = traceRunRepository;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<TraceRunUiState> getUiState() {
        return uiState;
    }

    public LiveData<Boolean> getProgressBarVisible() {
        return progressBarVisible;
    }

    public void updateStatus(String appName, String status) {
        uiState.postValue(TraceRunUiState.createStatusUpdate(appName, status));
    }

    public void updateProgress(int progress) {
        progressBarVisible.postValue(true);
        uiState.postValue(TraceRunUiState.createProgressUpdate(progress));
    }

    public void completeProgress(int iteration) {
        uiState.postValue(TraceRunUiState.createProgressComplete(iteration));
        if (iteration == 2) {
            progressBarVisible.postValue(false);
        }
    }

    public void showToast(String message) {
        uiState.postValue(TraceRunUiState.createShowToast(message));
    }

    public void showDialog(String title, String message, boolean exitReplays) {
        uiState.postValue(TraceRunUiState.createShowDialog(title, message, exitReplays));
    }

    // In the ViewModel
    public void selectAppsAndStartTrace(List<ApplicationBean> diffApps, List<ApplicationBean> inconclusiveApps, int which) {
        ArrayList<ApplicationBean> selectedApps = null;
        if (which == DialogInterface.BUTTON_POSITIVE) {
            selectedApps = new ArrayList<>(diffApps);
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            selectedApps = new ArrayList<>(inconclusiveApps);
        }

        for (ApplicationBean app : selectedApps) {
            app.setTomography(false);
            app.setArcepNeedsAlerting(false);
            app.setAlertFCC(false);
            app.setStatus(String.valueOf(R.string.pending));
        }
        inconclusiveApps.clear();
        diffApps.clear();
        traceRunRepository.execute();
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}