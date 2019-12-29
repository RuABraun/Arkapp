package typefree.typefree;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

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
        new renameAsyncTask(afileDao, this).execute(afile, cname, fname);
    }

    private static class renameAsyncTask extends AsyncTask<Object, Void, Void> {
        private AFileDao aSyncTaskaFileDao;
        private FileRepository fRepo;

        renameAsyncTask(AFileDao dao, FileRepository cls) { aSyncTaskaFileDao = dao; fRepo = cls;}
        @Override
        protected Void doInBackground(Object... objects) {
            AFile afile = (AFile) objects[0];
            String cname = (String) objects[1];
            String fname = (String) objects[2];
            if (fname.isEmpty()) {
                fname = fRepo.getFileName("No_name");
            }
            aSyncTaskaFileDao.rename(afile.getId(), fname, cname);
            Base.renameConv(afile.fname, fname);
            return null;
        }
    }

    public String getFileName(String cname) {
        final AtomicInteger fcount = new AtomicInteger();
        final FileRepository cls = this;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int num = cls.getNumFiles();
                fcount.set(num);
            }
        });
        t.setPriority(10);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int cnt = fcount.get() + 1;
        cname = cname.replaceAll("[ ?\\\\:\\/]", "_") + "_";
        String fname = cname + Integer.toString(cnt);
        String wavpath = filesdir + fname + ".wav";
        File f = new File(wavpath);
        while (f.exists()) {
            cnt++;
            fname = cname + Integer.toString(cnt);
            wavpath = filesdir + fname + ".wav";
            f = new File(wavpath);
        }
        return fname;
    }
}
