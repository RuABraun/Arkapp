package ark.ark;

import android.app.FragmentManager;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.util.List;


public class Manage extends Base {

    MyRecyclerAdapter adapter;
    private FileViewModel fviewmodel;
    private FileRepository f_repo;
    private FragmentManager fragManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage);
        this.setTitle("Ark - Files");

        fragManager = getFragmentManager();

        fviewmodel = ViewModelProviders.of(this).get(FileViewModel.class);
        f_repo = fviewmodel.repo;
        RecyclerView recview = findViewById(R.id.rv_files);
        recview.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyRecyclerAdapter(this, f_repo, fragManager);
        recview.setAdapter(adapter);


        fviewmodel.getAllFiles().observe(this, new Observer<List<AFile>>() {
            @Override
            public void onChanged(@Nullable List<AFile> aFiles) {
                adapter.setData(aFiles);
            }
        });

    }
}
