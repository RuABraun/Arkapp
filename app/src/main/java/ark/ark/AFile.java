package ark.ark;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class AFile {

    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo(name = "fname")
    public String fname;
    @ColumnInfo(name = "len_s")
    public int len_s;
    @ColumnInfo(name = "date")
    public String date;

    AFile(String fname, int len_s, String date) {
        this.fname = fname;
        this.len_s = len_s;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
}
