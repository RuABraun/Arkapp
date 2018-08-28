package ark.ark;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;

import java.io.File;
import java.util.List;

import static ark.ark.Base.filesdir;

public class FileRepository {

    private AFileDao afileDao;
    private LiveData<List<AFile>> files;

    FileRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        afileDao = db.afileDao();
        files = afileDao.getAllFiles();
    }

    LiveData<List<AFile>> getAllFiles() {
        return files;
    }

    int getNumFiles() {
        return afileDao.getCount();
    }


    public void insert(AFile afile) {
        new insertAsyncTask(afileDao).execute(afile);
    }

    private static class insertAsyncTask extends AsyncTask<AFile, Void, Void> {
        private AFileDao aSyncTaskaFileDao;

        insertAsyncTask(AFileDao dao) {
            aSyncTaskaFileDao = dao;
        }

        @Override
        protected Void doInBackground(final AFile... params) {
            aSyncTaskaFileDao.insert(params[0]);
            return null;
        }
    }

    public void delete(AFile afile) {
        new deleteAsyncTask(afileDao).execute(afile);
    }

    private static class deleteAsyncTask extends AsyncTask<AFile, Void, Void> {
        private AFileDao aSyncTaskaFileDao;

        deleteAsyncTask(AFileDao dao) {
            aSyncTaskaFileDao = dao;
        }

        @Override
        protected Void doInBackground(final AFile... params) {
            AFile af = params[0];
            aSyncTaskaFileDao.delete(af.getId());
            String[] suffixes = {".txt", ".ctm", ".wav"};
            String fname = af.fname;
            for (int i = 0; i < suffixes.length; i++) {
                File f = new File(filesdir + fname + suffixes[i]);
                if (f.exists()) {
                    f.delete();
                }
            }
            return null;
        }
    }
}
