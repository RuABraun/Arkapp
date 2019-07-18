package typefree.typefree;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CpuUsageInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.app.Fragment;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.Constraints;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bugsnag.android.Bugsnag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Base implements KeyboardHeightObserver {

    protected BottomNavigationView bottomNavigationView;
    protected RecEngine recEngine;
    protected FileRepository f_repo;
    Handler h_main = new Handler(Looper.getMainLooper());
    HandlerThread handlerThread;
    Handler h_background;
    private KeyboardHeightProvider keyboardHeightProvider;
    protected int keyboard_height;
    private ProgressBar pb_init;
    private TextView tv_init;
    private boolean start_main;

    private static List<String> mfiles = Arrays.asList("HCLG.fst", "final.mdl", "words.txt", "mfcc.conf", "align_lexicon.bin", "id_mapping.int",
            "final.raw", "ids.int", "ini.int", "mfcc.conf", "tf_model.tflite", "word2tag.int");
    protected FragmentManager fragmentManager;
    private static boolean perm_granted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSIONS_CODE = 200;
    protected PowerManager pm;
    final String PREFS_NAME = "MyPrefsFile";
    SharedPreferences settings;

    static {
        System.loadLibrary("rec-engine");
    }

    public native void native_load(AssetManager mgr, String rmodeldir);

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_PERMISSIONS_CODE:
                for(int i=0; i < grantResults.length; i++) {
                    if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Required permissions not granted, app closing.", Toast.LENGTH_LONG).show();
                        finish();
                        break;
                    }
                }
                break;
        }
        perm_granted = true;
        onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        Bugsnag.init(this);

        setContentView(R.layout.activity_main);

        fragmentManager = getFragmentManager();
        settings = getSharedPreferences(PREFS_NAME, 0);

        bottomNavigationView = findViewById(R.id.botNavig);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int item_id = item.getItemId();
                if (item_id == R.id.Manage) {
                    ManageFragment frag = new ManageFragment();
                    fragmentManager.beginTransaction().replace(R.id.fragment_container, frag, "manage").addToBackStack(null).commit();
                    return true;
                }
                if (item_id == R.id.Transcribe) {
                    MainFragment frag = new MainFragment();
                    fragmentManager.beginTransaction().replace(R.id.fragment_container, frag, "main").addToBackStack(null).commit();
                    return true;
                }
                if (item_id == R.id.Settings) {
                    Settings frag = new Settings();
                    fragmentManager.beginTransaction().replace(R.id.fragment_container, frag).addToBackStack(null).commit();
                    return true;
                }
                return true;
            }
        });

        // setting icon size?
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) bottomNavigationView.getChildAt(0);
        for (int i = 0; i < menuView.getChildCount(); i++) {
            final View iconView = menuView.getChildAt(i).findViewById(android.support.design.R.id.icon);
            final ViewGroup.LayoutParams layoutParams = iconView.getLayoutParams();
            final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, displayMetrics);
            layoutParams.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, displayMetrics);
            iconView.setLayoutParams(layoutParams);
        }

        pb_init = findViewById(R.id.pb_init);
        tv_init = findViewById(R.id.tv_init);

        // Handle permissions
        ArrayList<String> need_permissions = new ArrayList<>();
        for(String perm: permissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                need_permissions.add(perm);
            }
        }
        if (need_permissions.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    need_permissions.toArray(new String[need_permissions.size()]),
                    REQUEST_PERMISSIONS_CODE);
        } else {
            perm_granted = true;
        }

        keyboardHeightProvider = new KeyboardHeightProvider(this);
        View popupview = findViewById(R.id.main_root_view);
        popupview.post(new Runnable() {
            public void run() {
                keyboardHeightProvider.start();
            }
        });

        f_repo = new FileRepository(getApplication());
    }


    private void do_asr_setup() {

        Log.i("STORAGE", rootdir);
        File f = new File(rmodeldir);
        if (!f.exists()) f.mkdirs();
        File f2 = new File(filesdir);
        if (!f2.exists()) f2.mkdirs();
        boolean all_exist = true;
        for(String fname: mfiles) {
            File mf = new File(rmodeldir + fname);
            if (!mf.exists()) all_exist = false;
        }
        if (!all_exist) {
            start_main = false;
            Log.i("APP", "before getasset");
            mgr = getResources().getAssets();
            Log.i("APP", "extracting");
            pb_init.setVisibility(View.VISIBLE);
            tv_init.setVisibility(View.VISIBLE);
            bottomNavigationView.setVisibility(View.INVISIBLE);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    native_load(mgr, rmodeldir);
                    recEngine = RecEngine.getInstance(rmodeldir);
                }
            });
            t.setPriority(7);
            t.start();
            h_main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (RecEngine.isready) {
                        start_main = true;
                        Log.i("APP", "main is ready to go");
                        bottomNavigationView.setSelectedItemId(R.id.Transcribe);
                        tv_init.setVisibility(View.INVISIBLE);
                        pb_init.setVisibility(View.INVISIBLE);
                        bottomNavigationView.setVisibility(View.VISIBLE);
                    } else {
                        h_main.postDelayed(this, 50);
                    }
                }
            }, 100);
        } else {
            start_main = true;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    recEngine = RecEngine.getInstance(rmodeldir);
                }
            });
            t.setPriority(7);
            t.start();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!perm_granted) {
            finish();
            return;
        }
        handlerThread = new HandlerThread("BackgroundHandlerThread");
        handlerThread.start();

        do_asr_setup();
        if (start_main) {
            Log.i("APP", "Starting main fragment.");
            bottomNavigationView.setSelectedItemId(R.id.Transcribe);
        }

        h_background = new Handler(handlerThread.getLooper());

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isSustainedPerformanceModeSupported()) {
            getWindow().setSustainedPerformanceMode(true);
        }
//        int exclusiveCores[] = {};
//        exclusiveCores = android.os.Process.getExclusiveCores();
//        Log.i("APP", "numcore " + exclusiveCores.length);
//        for(int i = 0; i < exclusiveCores.length; i++) {
//            Log.i("APP", "core " + exclusiveCores[i]);
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handlerThread.quit();
        keyboardHeightProvider.close();
        if (pm.isSustainedPerformanceModeSupported()) {
            getWindow().setSustainedPerformanceMode(false);
        }
    }


    @Override
    protected void onDestroy() {
        Log.i("APP", "Destroying");
        recEngine.delete();
        super.onDestroy();
    }

    public void recordSwitch(View v) {
        if (is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.record_switch(v);
    }

    public void onCopyClick(View v) {
        if (is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.on_copy_click(v);
    }

    public void onShareClick(View v) {
        if (is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.on_share_click(v);
    }

    public void onEditClick(View v) {
        if (is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.on_edit_click(v);
    }

    public void onDeleteClick(View view) {
        if (is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.on_delete_click(view);
    }

    // Manage buttons
    public void onAddPress(View view) {
        if (is_spamclick()) return;
        ManageFragment frag = (ManageFragment) fragmentManager.findFragmentByTag("manage");
        frag.on_add_press(view);
    }

    // FileInfo buttons
    public void onMediaClick(View view) {
        if (is_spamclick()) return;
        FileInfo frag = (FileInfo) fragmentManager.findFragmentByTag("fileinfo");
        frag.on_media_click(view);
    }
    public void onEditClickFI(View view) {
        if (is_spamclick()) return;
        FileInfo frag = (FileInfo) fragmentManager.findFragmentByTag("fileinfo");
        frag.on_edit_click(view);
    }

    public static String getFileName(String cname, final FileRepository f_repo_) {
        final AtomicInteger fcount = new AtomicInteger();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int num = f_repo_.getNumFiles();
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


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        View v = getCurrentFocus();
        boolean ret = super.dispatchTouchEvent(event);
        MainFragment frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        if (frag != null && frag.isVisible()) {
            frag.handle_touch_event(v, event);
            return ret;
        }
        FileInfo fragb = (FileInfo) fragmentManager.findFragmentByTag("fileinfo");
        if (fragb != null && fragb.isVisible()) {
            fragb.handle_touch_event(v, event);
            return ret;
        }
        return ret;
    }

    @Override
    public void onPause() {
        super.onPause();
        keyboardHeightProvider.setKeyboardHeightObserver(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        keyboardHeightProvider.setKeyboardHeightObserver(this);
    }

    @Override
    public void onKeyboardHeightChanged(int height, int orientation) {
        Log.i("APP", "keyboard height " + height);
        keyboard_height = height;
        MainFragment frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        if (frag != null && frag.isVisible()) {
            frag.resize_views(height);
            return;
        }
        FileInfo fragb = (FileInfo) fragmentManager.findFragmentByTag("fileinfo");
        if (fragb != null && fragb.isVisible()) {
            fragb.resize_views(height);
            return;
        }

    }
}


