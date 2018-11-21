package fingerless.fingerless;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;

import java.util.List;

public class FileViewModel extends AndroidViewModel {
    public FileRepository repo;
    private LiveData<List<AFile>> files;

    public FileViewModel(Application application) {
        super(application);
        repo = new FileRepository(application);
        files = repo.getAllFiles();
    }

    LiveData<List<AFile>> getAllFiles() {
        return files;
    }

    public void insert(AFile afile) {
        repo.insert(afile);
    }
}
