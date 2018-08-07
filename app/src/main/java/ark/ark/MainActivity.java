package ark.ark;

import android.Manifest;
import android.content.Context;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends Base {

    public boolean is_recording = false;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
                                     Manifest.permission.READ_EXTERNAL_STORAGE,
                                     Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSIONS_CODE = 200;
    private static List<String> mfiles = Arrays.asList("HCLG.fst", "final.mdl", "words.txt", "fbank.conf", "align_lexicon.int");
    Handler h = new Handler(Looper.getMainLooper());
    Runnable runnable;
    private FileRepository f_repo;

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
        }

        f_repo = new FileRepository(getApplication());
    }

    private void do_setup() {
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
            native_load(mgr, rmodeldir);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        TextView tv = findViewById(R.id.rec_help_text);
        tv.setText(R.string.LoadingMsg);
        do_setup();
        createRecEng(rmodeldir);
        h.postDelayed(new Runnable() {
            public void run() {
                runnable=this;
                if (RecEngine.isready) {
                    TextView tv = findViewById(R.id.rec_help_text);
                    tv.setText(R.string.HelloMsg);
                    h.removeCallbacks(runnable);
                }
                h.postDelayed(runnable, 200);
            }
        }, 200);
    }

    @Override
    protected void onStop() {
        RecEngine.delete();
        super.onStop();
    }

    public void record_switch(View view) {
        if (is_spamclick()) return;

        Button button_rec = findViewById(R.id.button_rec);
        Log.i("APP", "isrecording: " + String.valueOf(is_recording));
        if (!is_recording ) {
            if (!RecEngine.isready) return;
            String fpath= filesdir + "tmpfile";
            RecEngine.transcribe_stream(fpath);
            is_recording = true;
            button_rec.setText(R.string.button_recstop);

            h.postDelayed(new Runnable() {
                public void run() {
                    runnable=this;
                    update_text();
                    h.postDelayed(runnable, 500);
                }
            }, 500);
        } else {

            is_recording = false;
            button_rec.setText(R.string.button_recstart);
            h.removeCallbacks(runnable);

            long num_out_frames = RecEngine.stop_trans_stream();

            String fpath = getFileName();
            String[] suffixes = {".txt", ".ctm", ".wav"};
            String name = new File(fpath).getName();
            File from, to;
            for (int i = 0; i < suffixes.length; i++) {
                from = new File(filesdir + "tmpfile" + suffixes[i]);
                to = new File(fpath + suffixes[i]);
                from.renameTo(to);
            }

            int duration_s = (int) (3 * num_out_frames) / 100;
            AFile afile = new AFile(name, duration_s, name);
            f_repo.insert(afile);
        }
    }

    public String getFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
        Date now = new Date();
        String name = sdf.format(now);
        String fpath = filesdir + name + "_file";
        String wavpath = fpath + ".wav";
        int i = 1;
        while (new File(wavpath).exists()) {
            fpath = fpath + "_" + Integer.toString(i);
            wavpath = fpath  + ".wav";
            i++;
        }
        return fpath;
    }

    public void update_text() {
        String str = RecEngine.get_text();
        EditText ed = findViewById(R.id.transText);
        ed.setText(str, TextView.BufferType.EDITABLE);
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
