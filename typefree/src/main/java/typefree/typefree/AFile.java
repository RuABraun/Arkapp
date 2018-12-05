package typefree.typefree;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.os.Parcel;
import android.os.Parcelable;

@Entity
public class AFile implements Parcelable {

    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "title")
    public String title;
    @ColumnInfo(name = "fname")
    public String fname;
    @ColumnInfo(name = "len_s")
    public int len_s;
    @ColumnInfo(name = "date")
    public String date;

    AFile(String title, String fname, int len_s, String date) {
        this.title = title;
        this.fname = fname;
        this.len_s = len_s;
        this.date = date;
    }

    AFile(AFile afile) {
        this.title = afile.title;
        this.fname = afile.fname;
        this.len_s = afile.len_s;
        this.date = afile.date;
        this.id = afile.id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFname() {
        return fname;
    }

    public int getLen_s() {
        return len_s;
    }

    public String getDate() {
        return date;
    }

    public void setFname(String fname) {
        this.fname = fname;
    }

    public void setLen_s(int len_s) {
        this.len_s = len_s;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(id);
        out.writeString(title);
        out.writeString(fname);
        out.writeInt(len_s);
        out.writeString(date);
    }

    public static final Parcelable.Creator<AFile> CREATOR = new Parcelable.Creator<AFile>() {
        public AFile createFromParcel(Parcel in) {
            return new AFile(in);
        }
        @Override
        public AFile[] newArray(int size) {
            return new AFile[size];
        }
    };

    private AFile(Parcel in) {
        id = in.readInt();
        title = in.readString();
        fname = in.readString();
        len_s = in.readInt();
        date = in.readString();
    }
}
