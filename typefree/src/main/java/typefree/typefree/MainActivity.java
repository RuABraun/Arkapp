package typefree.typefree;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bugsnag.android.Bugsnag;
import com.google.android.gms.iid.InstanceID;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

import java.io.File;
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
    public int fragment_id = 1;

    private static List<String> mfiles = Arrays.asList("HCLG.fst", "final.mdl", "words.txt", "mfcc.conf", "word_boundary.int",
            "mfcc.conf", "traced_model.pt", "word2tag.int", "o3_2p5M.carpa", "means.vec", "cmvn.conf");
    private static List<String> rnn_files = Arrays.asList("final.raw", "id_mapping.bin", "ini.int", "ids.int", "word_embedding_large.final.mat",
            "word_embedding_med.final.mat", "word_embedding_small.final.mat");
    protected FragmentManager fragmentManager;
    private static boolean perm_granted = false;
    private Runnable startup_runnable;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSIONS_CODE = 200;
    protected PowerManager pm;
    final String PREFS_NAME = "MyPrefsFile";
    SharedPreferences settings;
    private AppUpdateManager appUpdateManager;
    int update_request_code = 123;
    private MainActivity baseact = this;
    int exclusiveCores[] = {};
    public boolean just_imported_file = false;
    public boolean finished_conversion = false;

    static {
        System.loadLibrary("rec-engine");
    }

    public native void native_load(AssetManager mgr, String rmodeldir);

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_PERMISSIONS_CODE:
                boolean failed = false;
                for(int i=0; i < grantResults.length; i++) {
                    if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        failed = true;
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Warning")
                                .setMessage("You must grant these permissions for the app to work.")
                                .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        AlertDialog alert = builder.create();
                        alert.show();
                        h_main.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                alert.dismiss();
                                finish();
                            }
                        }, 3000);
                        break;
                    }
                }
                if (!failed) {
                    perm_granted = true;
                }
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);
        Bugsnag.init(this);
        Log.i("APP-MAINACTIVITY", "In onCreate");
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();
        settings = getSharedPreferences(PREFS_NAME, 0);

        bottomNavigationView = findViewById(R.id.botNavig);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int item_id = item.getItemId();
                if (item_id == R.id.Manage) {
                    ManageFragment frag = (ManageFragment) fragmentManager.findFragmentByTag("manage");
                    if (frag == null || frag.isRemoving()) {
                        frag = new ManageFragment();
                        fragmentManager.beginTransaction().replace(R.id.fragment_container, frag, "manage").addToBackStack(null).commitAllowingStateLoss();
                    }
                    fragment_id = 2;
                    return true;
                }
                if (item_id == R.id.Transcribe) {
                    MainFragment frag = (MainFragment) fragmentManager.findFragmentByTag("main");
                    if (frag == null || frag.isRemoving()) {
                        frag = new MainFragment();
                        fragmentManager.beginTransaction().replace(R.id.fragment_container, frag, "main").addToBackStack(null).commitAllowingStateLoss();
                    }
                    fragment_id = 1;
                    return true;
                }
                if (item_id == R.id.Settings) {
                    Settings frag = (Settings) fragmentManager.findFragmentByTag("extra");
                    if (frag == null || frag.isRemoving()) {
                        frag = new Settings();
                        fragmentManager.beginTransaction().replace(R.id.fragment_container, frag, "extra").addToBackStack(null).commitAllowingStateLoss();
                    }
                    fragment_id = 3;
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

        keyboardHeightProvider = new KeyboardHeightProvider(this);
        View popupview = findViewById(R.id.main_root_view);
        popupview.post(new Runnable() {
            public void run() {
                keyboardHeightProvider.start();
            }
        });

        f_repo = new FileRepository(getApplication());

        ArrayList<String> need_permissions = new ArrayList<>();
        for(String perm: permissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                need_permissions.add(perm);
            }
        }
        if (need_permissions.size() == 0) {
            perm_granted = true;
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Info about permissions")
                    .setMessage("So that this app can use the phone's microphone and so you can import " +
                            " audio files on your phone into the app it will ask for permission to get access to the microphone and the phone's files.\n" +
                            "The app will never access/share your files on its own.")
                    .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            ask_for_permissions(need_permissions);
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private void ask_for_permissions(ArrayList<String> need_permissions) {
        if (need_permissions.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    need_permissions.toArray(new String[need_permissions.size()]),
                    REQUEST_PERMISSIONS_CODE);
        } else {
            perm_granted = true;
        }
    }

    private void do_asr_setup() {

        Log.i("STORAGE", rootdir);
        File f = new File(rmodeldir);
        if (!f.exists()) f.mkdirs();
        File f2 = new File(filesdir);
        if (!f2.exists()) f2.mkdirs();
        boolean all_exist = true;
        ArrayList<String> all_files = new ArrayList<>();
        all_files.addAll(mfiles);
        all_files.addAll(rnn_files);
        for(String fname: all_files) {
            File mf = new File(rmodeldir + fname);
            if (!mf.exists()) {
                Log.i("APP", "File missing " + fname);
                all_exist = false;
            }
        }
//        for (String fname: rnn_files) {
//            File mf = new File(rmodeldir + fname);
//            if (!mf.exists()) {
//                boolean is_deleted = mf.delete();
//                Bugsnag.leaveBreadcrumb("Deleting old RNN file " + is_deleted);
//            }
//        }
        if (!all_exist) {
            start_main = false;
            Bugsnag.leaveBreadcrumb("Extracting model as does not exist on filesystem yet.");
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
                    Bugsnag.leaveBreadcrumb("Begin loading model.");
                    recEngine = RecEngine.getInstance(rmodeldir, exclusiveCores);
                    Bugsnag.leaveBreadcrumb("Done loading model");
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
                        tv_init.setVisibility(View.GONE);
                        pb_init.setVisibility(View.GONE);
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
                    Bugsnag.leaveBreadcrumb("Begin loading model.");
                    recEngine = RecEngine.getInstance(rmodeldir, exclusiveCores);
                    Bugsnag.leaveBreadcrumb("Done loading model");
                }
            });
            t.setPriority(7);
            t.start();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        String uniqueID = InstanceID.getInstance(this).getId();
        Bugsnag.setUser(uniqueID, "email", "user");
        Log.i("APP", "Starting up main activity.");
        Bugsnag.leaveBreadcrumb("Starting up main activity.");

        appUpdateManager = AppUpdateManagerFactory.create(this);
        if (!settings.getBoolean("knows_is_first_start", true)) {
            Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

            appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Update available!").setMessage("An improved version of the app is available!\n" +
                        "However, this may take a while to download and you can not use the app while doing so.\n" +
                            "Do you want to update now?")
                            .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }).setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            try {
                                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE,
                                        baseact, update_request_code);
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();
                }
            });
        } else {
            settings.edit().putBoolean("knows_conv_save", false).apply();
        }

        handlerThread = new HandlerThread("BackgroundHandlerThread");
        handlerThread.start();

        try {
            exclusiveCores = android.os.Process.getExclusiveCores();
            Log.i("APP", "numcore " + exclusiveCores.length);
        } catch (RuntimeException e) {
            ;
        }

        h_background = new Handler(handlerThread.getLooper());

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        startup_runnable = new Runnable() {
            @Override
            public void run() {
                if (!perm_granted) {
                    h_main.postDelayed(this, 500);
                } else {
                    do_asr_setup();
                    if (start_main) {
                        Log.i("APP", "Starting appropriate fragment.");
                        if (fragment_id == 1) {
                            bottomNavigationView.setSelectedItemId(R.id.Transcribe);
                        } else if (fragment_id == 2) {
                            bottomNavigationView.setSelectedItemId(R.id.Manage);
                        } else if (fragment_id == 3) {
                            bottomNavigationView.setSelectedItemId(R.id.Settings);
                        }
                    }

                }
            }
        };
        h_main.post(startup_runnable);
    }

    @Override
    protected void onStop() {
        if (handlerThread != null) {
            handlerThread.quit();
        }
        keyboardHeightProvider.close();
        Bugsnag.leaveBreadcrumb("Stopping activity.");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i("APP", "Destroying");
        Bugsnag.leaveBreadcrumb("Destroying");
        if (recEngine != null) {
            recEngine.delete();
        }
        super.onDestroy();
    }

    public void recordSwitch(View v) {
        if (Base.is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.record_switch(v);
    }

    public void pauseSwitch(View v) {
        if (Base.is_spamclick()) return;
        MainFragment main_frag = (MainFragment) fragmentManager.findFragmentByTag("main");
        main_frag.pause_switch(v);
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
        Log.i("APP", "Resuming activity");
        super.onResume();
        appUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(appUpdateInfo -> {
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, update_request_code);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                });
        keyboardHeightProvider.setKeyboardHeightObserver(this);
    }

    @Override
    public void onKeyboardHeightChanged(int height, int orientation) {
        //Log.i("APP", "keyboard height " + height);
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


