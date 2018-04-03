package ark.ark;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

        do_setup();

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(R.string.HelloMsg);

        Log.i("MYAPP", "Starting!");
    }

    public void record_switch(View view) {
        Button button_rec = findViewById(R.id.button_rec);
        if (!is_recording ) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm");
            Date now = new Date();
            String name = filesdir + sdf.format(now);
            String fname = name + ".wav";

            while (new File(fname).exists()) {
                int i = 1;
                fname = name + "_" + Integer.toString(i) + ".wav";
                i++;
            }
            RecEngine.create(fname);
            is_recording = true;
            button_rec.setText(R.string.button_recstop);
        } else {
            RecEngine.delete();
            is_recording = false;
            button_rec.setText(R.string.button_recstart);
        }
    }

    private void do_setup() {
        Log.i("STORAGE", rootdir);
        File f = new File(modeldir);
        if (!f.exists()) f.mkdirs();
        File f2 = new File(filesdir);
        if (!f2.exists()) f2.mkdirs();
        boolean all_exist = true;
        for(String fname: mfiles) {
            File mf = new File(modeldir + fname);
            if (!mf.exists()) all_exist = false;
        }
        if (!all_exist) {
            mgr = getResources().getAssets();
            load(mgr, modeldir);
        }
    }

    @Override
    protected void onDestroy() {
        RecEngine.delete();
        super.onDestroy();
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("rec-engine");
    }
}
