package ark.ark;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface AFileDao {

    @Query("SELECT * FROM AFile")
    LiveData<List<AFile>> getAllFiles();

    @Insert
    void insert(AFile elem);

    @Query("DELETE FROM AFile WHERE id = :elemid")
    void delete(int elemid);

}
