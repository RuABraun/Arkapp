package typefree.typefree;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static typefree.typefree.Base.file_suffixes;
import static typefree.typefree.Base.filesdir;

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

    AFile getById(long id) { return afileDao.getById(id); }

    public long insert(AFile afile) {
        AsyncTask task = new insertAsyncTask(afileDao).execute(afile);
        long id = -1;
        try {
            id = (long) task.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return id;
    }

    private static class insertAsyncTask extends AsyncTask<AFile, Void, Long> {
        private AFileDao aSyncTaskaFileDao;

        insertAsyncTask(AFileDao dao) {
            aSyncTaskaFileDao = dao;
        }

        @Override
        protected Long doInBackground(final AFile... params) {
            return aSyncTaskaFileDao.insert(params[0]);
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
            String fname = af.fname;
            Log.i("APP", "Deleting " + fname);
            for(Object suffix : file_suffixes.values()) {
                File f = new File(filesdir + fname + suffix);
                if (f.exists()) {
                    f.delete();
                } else {
                    Log.i("APP","Tried to delete file that does not exist " + f.getPath());
                }
            }
            return null;
        }
    }

    public void rename(AFile afile_old, String cname, String fname) {
        AFile afile = new AFile(afile_old);
        new renameAsyncTask(afileDao).execute(afile, cname, fname);
    }

    private static class renameAsyncTask extends AsyncTask<Object, Void, Void> {
        private AFileDao aSyncTaskaFileDao;

        renameAsyncTask(AFileDao dao) { aSyncTaskaFileDao = dao; }
        @Override
        protected Void doInBackground(Object... objects) {
            AFile afile = (AFile) objects[0];
            String cname = (String) objects[1];
            String fname = (String) objects[2];
            Log.i("APP", "Old " + afile.fname + ", new name: " + fname);
            aSyncTaskaFileDao.rename(afile.getId(), fname, cname);
            Log.i("APP", "Old " + afile.fname + ", new name: " + fname);
            Base.renameConv(afile.fname, fname);
            return null;
        }
    }
}
