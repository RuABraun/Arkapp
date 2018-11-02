package ark.ark;

import android.app.FragmentManager;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class Manage extends Base {

    MyRecyclerAdapter adapter;
    private FileViewModel fviewmodel;
    private FileRepository f_repo;
    private FragmentManager fragManager;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        setContentView(R.layout.activity_manage);
        this.setTitle("Ark - Files");

        bottomNavigationView = findViewById(R.id.botNavigB);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int item_id = item.getItemId();
                Intent intent;
                if (item_id == R.id.Manage) {
                    return true;
                }
                if (item_id == R.id.Transcribe) {
                    intent = new Intent(getApplicationContext(), MainActivity.class);
                    startActivity(intent);
                    return true;
                }
                if (item_id == R.id.Settings) {
                    intent = new Intent(getApplicationContext(), Settings.class);
                    startActivity(intent);
                    return true;
                }
                return true;
            }
        });
        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.botNavigB);
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) bottomNavigationView.getChildAt(0);
        for (int i = 0; i < menuView.getChildCount(); i++) {
            final View iconView = menuView.getChildAt(i).findViewById(android.support.design.R.id.icon);
            final ViewGroup.LayoutParams layoutParams = iconView.getLayoutParams();
            final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, displayMetrics);
            layoutParams.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, displayMetrics);
            iconView.setLayoutParams(layoutParams);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        getMenuInflater().inflate(R.menu.menu_manage, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();

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
                Collections.sort(aFiles, new Comparator<AFile>() {
                    @Override
                    public int compare(AFile lhs, AFile rhs) {
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd/MM/yyyy HH:mm", Locale.getDefault());
                        Date left_date = null;
                        try {
                            left_date = sdf.parse(lhs.date);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        Date right_date = null;
                        try {
                            right_date = sdf.parse(rhs.date);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return right_date.compareTo(left_date);
                    }
                });
                adapter.setData(aFiles);
            }
        });
//        if (adapter.getItemCount() == 0) {
//            Log.i("APP", "IN HERE");
//            tv.setVisibility(View.VISIBLE);
//        } else {
//            Log.i("APP", "IN HERE B");

    }

    public void share(String fname, ArrayList checked) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("message/rfc822");
        ArrayList<Uri> files = new ArrayList<>();

        int i = 0;
        for(Object suffix : file_suffixes.values()) {
            if (!checked.contains(i)) continue;
            File f = new File(Base.filesdir, fname + suffix);
            files.add(Uri.fromFile(f));
            i++;
        }
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share file(s)"));
    }

}
