package fingerless.fingerless;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface AFileDao {

    @Query("SELECT * FROM AFile")
    LiveData<List<AFile>> getAllFiles();

    @Query("SELECT * FROM AFile WHERE id = :elemid")
    AFile getById(long elemid);

    @Insert
    long insert(AFile elem);

    @Query("DELETE FROM AFile WHERE id = :elemid")
    void delete(int elemid);

    @Query("SELECT COUNT(id) FROM AFile")
    int getCount();

    @Query("UPDATE AFile SET title=:cname, fname=:fname WHERE id=:elemid")
    void rename(int elemid, String fname, String cname);

}
