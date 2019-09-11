package mobi.meddle.wehe.bean;

import android.os.Parcel;
import android.os.Parcelable;

public class ApplicationBean implements Parcelable {
    public static final Parcelable.Creator<ApplicationBean> CREATOR = new Parcelable.Creator<ApplicationBean>() {
        public ApplicationBean createFromParcel(Parcel in) {
            return new ApplicationBean(in);
        }

        public ApplicationBean[] newArray(int size) {
            return new ApplicationBean[size];
        }
    };
    public String name = null;
    public String status = "Waiting to start";
    public double time = 0.0;
    public double randomTime = 0.0;
    public int historyCount = -1;
    public boolean error = false;
    public double area_test = 0;
    public double ks2pVal = 0;
    public double ks2pRatio = 0;
    public double xputOriginal = 0;
    public double xputTest = 0;
    private String dataFile = null;
    private String randomDataFile = null;
    private double size;
    private double randomSize;
    private boolean isSelected = false;
    private String image = null;

    public ApplicationBean() {
    }

    private ApplicationBean(Parcel in) {
        name = in.readString();
        dataFile = in.readString();
        size = in.readDouble();
        historyCount = in.readInt();
        boolean[] arr = new boolean[1];
        in.readBooleanArray(arr);
        isSelected = arr[0];
        image = in.readString();
        time = in.readDouble();
        randomDataFile = in.readString();
        randomTime = in.readDouble();
        randomSize = in.readDouble();
        xputOriginal = in.readDouble();
        xputTest = in.readDouble();
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getRandomTime() {
        return randomTime;
    }

    public void setRandomTime(double randomTime) {
        this.randomTime = randomTime;
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

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double getRandomSize() {
        return randomSize;
    }

    public void setRandomSize(double randomSize) {
        this.randomSize = randomSize;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(dataFile);
        dest.writeDouble(size);
        dest.writeInt(historyCount);
        dest.writeBooleanArray(new boolean[]{isSelected});
        dest.writeString(image);
        dest.writeDouble(time);
        dest.writeString(randomDataFile);
        dest.writeDouble(randomTime);
        dest.writeDouble(randomSize);
        dest.writeDouble(xputOriginal);
        dest.writeDouble(xputTest);
    }
}
