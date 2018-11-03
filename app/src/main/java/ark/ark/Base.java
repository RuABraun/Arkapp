package ark.ark;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Base extends AppCompatActivity {

    public static AssetManager mgr;
    public static String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Ark/";
    public static String filesdir = rootdir + "files/";
    public static String rmodeldir = rootdir + "model/";
    public static HashMap file_suffixes = new HashMap<String, String>() {
        {
            put("text", ".txt");
            put("audio", ".wav");
            put("timed", "_timed.txt");
        }
    };
    private long time_lastclick = 0;

    public native void native_load(AssetManager mgr, String rmodeldir);

    public boolean is_spamclick() {
        if (SystemClock.elapsedRealtime() - time_lastclick < 500) {
            return true;
        }
        time_lastclick = SystemClock.elapsedRealtime();
        return false;
    }

    public void share(String fname, ArrayList checked) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("message/rfc822");
        ArrayList<Uri> files = new ArrayList<>();
        int num = Base.file_suffixes.size();

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

    public static void renameConv(String fname_old, String fname) {
        File from, to;
        for(Object suffix : file_suffixes.values()) {
            from = new File(filesdir + fname_old + suffix);
            to = new File(filesdir + fname + suffix);
            from.renameTo(to);
        }
    }
}


