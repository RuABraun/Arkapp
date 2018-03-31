package ark.ark;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Base {

    public boolean is_recording = false;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
                                     Manifest.permission.READ_EXTERNAL_STORAGE,
                                     Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSIONS_CODE = 200;

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

        File rootdir = new File(getString(R.string.SaveDir));
        if (!rootdir.exists()) rootdir.mkdirs();

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(R.string.HelloMsg);

        String fpath = getExternalFilesDir(Environment.DIRECTORY_MUSIC).getAbsolutePath();
        Log.i("MYAPP", "Starting!");
    }

    public void record_switch(View view) {
        Button button_rec = findViewById(R.id.button_rec);
        if (!is_recording ) {
            RecEngine.create();
            is_recording = true;
            button_rec.setText(R.string.button_recstop);
        } else {
            RecEngine.delete();
            is_recording = false;
            button_rec.setText(R.string.button_recstart);
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
