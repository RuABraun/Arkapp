package ark.ark;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {AFile.class}, version = 1)
public abstract class AppDatabase  extends RoomDatabase {
    public abstract AFileDao afileDao();

    private static AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(), AppDatabase.class, "db_files").build();
                }
            }
        }
        return INSTANCE;
    }

}

