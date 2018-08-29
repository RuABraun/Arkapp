package ark.ark;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Base {

    public boolean is_recording = false;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
                                     Manifest.permission.READ_EXTERNAL_STORAGE,
                                     Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSIONS_CODE = 200;
    private static boolean perm_granted = false;
    private static List<String> mfiles = Arrays.asList("HCLG.fst", "final.mdl", "words.txt", "mfcc.conf", "align_lexicon.int");
    Handler h = new Handler(Looper.getMainLooper());
    Runnable runnable;
    private FileRepository f_repo;
    private RecEngine recEngine;
    private ImageButton bt_pause;
    private ImageButton bt_rec;
    private EditText ed_transtext;
    final String PREFS_NAME = "MyPrefsFile";
    Thread t_del, t_stoptrans;

    static {
        System.loadLibrary("rec-engine");
    }

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
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (settings.getBoolean("is_first_time", true)) {
            Log.i("APP", "Running for the first time.");
            t_del = new Thread(new Runnable() {
                @Override
                public void run() {
                    String[] dirs = {rmodeldir, filesdir};
                    for(String dir: dirs) {
                        File d = new File(dir);
                        for(File fobj: d.listFiles()) {
                            fobj.delete();
                        }
                        d.delete();
                    }
                }
            });
            settings.edit().putBoolean("is_first_time", false).apply();
            t_del.setPriority(6);
            t_del.start();
        }
        onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

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

        f_repo = new FileRepository(getApplication());

    }

    private void do_setup() {
        try {
            if (t_del != null) {
                t_del.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
            mgr = getResources().getAssets();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    native_load(mgr, rmodeldir);
                    recEngine = RecEngine.getInstance(rmodeldir);
                }
            });
            t.setPriority(6);
            t.start();
        } else {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    recEngine = RecEngine.getInstance(rmodeldir);
                }
            });
            t.setPriority(6);
            t.start();
        }
    }


    @Override
    public void onStart() {
        super.onStart();

        TextView tv = findViewById(R.id.rec_help_text);
        tv.setText(R.string.LoadingMsg);
        if (!perm_granted) {
            return;
        }
        do_setup();
        h.postDelayed(new Runnable() {
            public void run() {
                runnable=this;
                if (recEngine.isready) {
                    TextView tv = findViewById(R.id.rec_help_text);
                    tv.setText(R.string.HelloMsg);
                    h.removeCallbacks(runnable);
                }
                h.postDelayed(runnable, 200);
            }
        }, 200);

        bt_pause = findViewById(R.id.button_pause);
        bt_pause.setVisibility(View.INVISIBLE);

        bt_rec = findViewById(R.id.button_rec);

        ed_transtext = findViewById(R.id.transText);
    }

    @Override
    protected void onDestroy() {
        Log.i("APP", "Destroying");
        recEngine.delete();
        super.onDestroy();
    }

    public void stop_transcribe() {
        long num_out_frames = recEngine.stop_trans_stream();
        h.removeCallbacks(runnable);

        String fname = getDefaultFileName();
        String date = getFileDate();
        String title = "Conversation";

        String[] suffixes = {".txt", "_timed.txt", ".wav"};
        File from, to;
        for (int i = 0; i < suffixes.length; i++) {
            from = new File(filesdir + "tmpfile" + suffixes[i]);
            to = new File(filesdir + fname + suffixes[i]);
            from.renameTo(to);
        }

        int duration_s = (int) (3 * num_out_frames) / 100;
        AFile afile = new AFile(title, fname, duration_s, date);
        f_repo.insert(afile);
    }

    public void record_switch(View view) {
        if (is_spamclick()) return;

        Log.i("APP", "isrecording: " + String.valueOf(is_recording));
        if (!is_recording ) {
            if (!RecEngine.isready) return;
            try {
                if (t_stoptrans != null) t_stoptrans.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ed_transtext.setText("", TextView.BufferType.EDITABLE);
            String fpath= filesdir + "tmpfile";
            recEngine.transcribe_stream(fpath);
            is_recording = true;
            bt_rec.setImageResource(R.drawable.mic_off);

            h.postDelayed(new Runnable() {
                public void run() {
                    runnable=this;
                    update_text();
                    h.postDelayed(runnable, 500);
                }
            }, 500);

            bt_pause.setVisibility(View.VISIBLE);
        } else {
            bt_pause.setVisibility(View.INVISIBLE);
            is_recording = false;
            bt_rec.setImageResource(R.drawable.mic);
            t_stoptrans = new Thread(new Runnable() {
                @Override
                public void run() {
                    stop_transcribe();
                }
            });
            t_stoptrans.setPriority(6);
            t_stoptrans.start();
        }
    }

    public String getDefaultFileName() {
        Log.i("APP", "Getting file title");
        final AtomicInteger fcount = new AtomicInteger();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int num = f_repo.getNumFiles();
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
        String fname = "Conversation_" + Integer.toString(cnt);
        String wavpath = fname + ".wav";
        while (new File(wavpath).exists()) {
            cnt++;
            fname = "Conversation_" + Integer.toString(cnt);
            wavpath = fname + ".wav";
        }
        return fname;
    }

    public String getFileDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Date now = new Date();
        String date = sdf.format(now);
        return date;
    }

    public void update_text() {
        String str = recEngine.get_text();
        ed_transtext.setText(str, TextView.BufferType.EDITABLE);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        View v = getCurrentFocus();
        boolean ret = super.dispatchTouchEvent(event);

        if (v instanceof EditText) {
            View w = getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];

            //Log.d("Activity", "Touch event "+event.getRawX()+","+event.getRawY()+" "+x+","+y+" rect "+w.getLeft()+","+w.getTop()+","+w.getRight()+","+w.getBottom()+" coords "+scrcoords[0]+","+scrcoords[1]);
            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom()) ) {

                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);
            }
        }
        return ret;
    }

}
