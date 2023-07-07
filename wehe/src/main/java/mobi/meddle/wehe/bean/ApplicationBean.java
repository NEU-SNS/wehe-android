package mobi.meddle.wehe.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Data structure to hold info about an app/port
 */
public class ApplicationBean implements Parcelable {
    public enum Category {
        VIDEO, MUSIC, CONFERENCING, SMALL_PORT, LARGE_PORT
    }

    public static final Parcelable.Creator<ApplicationBean> CREATOR =
            new Parcelable.Creator<ApplicationBean>() {
                public ApplicationBean createFromParcel(Parcel in) {
                    return new ApplicationBean(in);
                }

                public ApplicationBean[] newArray(int size) {
                    return new ApplicationBean[size];
                }
            };
    public double area_test = 0;
    public double ks2pVal = 0;
    public double ks2pRatio = 0;
    public double originalThroughput = 0; //original replay throughput for apps
    public double randomThroughput = 0; //random replay throughput for apps; throughput for port 443
    private String name = null; //name of the app
    private String status = "Waiting to start"; //status of the replays
    private String error = "";
    //number of seconds needed to run both replays; port time is not accurate, as the goal is to
    //run those as fast as the user's internet connection can support
    private int time = 0;
    private int historyCount = -1; //the ID for the replay for this specific user
    private int size = 0; //size in MB of both replays together
    private String dataFile = null; //filename of the replay
    private String randomDataFile = null; //filename of the random replay for apps; port 443 for ports
    private boolean isSelected = false; //true if checked when choosing which apps/ports to run
    private String image = null; //filename of the app/port image
    private boolean englishOnly = false; //app displayed only in english version
    private boolean frenchOnly = false; //app displayed only in french version
    //true if there is differentiation and the app is in French; a button will pop up allowing the
    //user to alert arcep; will become false when the user alerts arcep
    private boolean arcepNeedsAlerting = false;
    private Category cat; //category that the app belongs in
    private boolean isLocalization = false;
    private String differentiationNetwork = ""; //network that caused differentiation

    public ApplicationBean() {

    }

    private ApplicationBean(@NonNull Parcel in) {
        Log.d("ApplicationBean", "Private constructor called");
        name = in.readString();
        dataFile = in.readString();
        size = in.readInt();
        historyCount = in.readInt();
        boolean[] arr = new boolean[1];
        in.readBooleanArray(arr);
        isSelected = arr[0];
        image = in.readString();
        time = in.readInt();
        randomDataFile = in.readString();
        originalThroughput = in.readDouble();
        randomThroughput = in.readDouble();
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getHistoryCount() {
        return historyCount;
    }

    public void setHistoryCount(int historyCount) {
        this.historyCount = historyCount;
    }

    public String getDataFile() {
        return dataFile;
    }

    public void setDataFile(String dataFile) {
        this.dataFile = dataFile;
    }

    public String getRandomDataFile() {
        return randomDataFile;
    }

    public void setRandomDataFile(String randomDataFile) {
        this.randomDataFile = randomDataFile;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    public boolean isEnglishOnly() {
        return englishOnly;
    }

    public void setEnglishOnly(boolean val) {
        this.englishOnly = val;
    }

    public boolean isFrenchOnly() {
        return frenchOnly;
    }

    public void setFrenchOnly(boolean val) {
        this.frenchOnly = val;
    }

    public boolean getArcepNeedsAlerting() {
        return arcepNeedsAlerting;
    }

    public void setArcepNeedsAlerting(boolean val) {
        this.arcepNeedsAlerting = val;
    }

    public void setCategory(Category cat) {
        this.cat = cat;
    }

    public Category getCategory() {
        return cat;
    }

    public boolean isLocalization() {
        return isLocalization;
    }

    public void setLocalization(boolean isLocalization) {
        this.isLocalization = isLocalization;
    }

    public String getDifferentiationNetwork() {
        return differentiationNetwork;
    }

    public void setDifferentiationNetwork(String differentiationNetwork) {
        this.differentiationNetwork = differentiationNetwork;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(dataFile);
        dest.writeInt(size);
        dest.writeInt(historyCount);
        dest.writeBooleanArray(new boolean[]{isSelected});
        dest.writeString(image);
        dest.writeInt(time);
        dest.writeString(randomDataFile);
        dest.writeDouble(originalThroughput);
        dest.writeDouble(randomThroughput);
    }
}
