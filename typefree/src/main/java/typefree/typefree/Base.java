package typefree.typefree;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Base extends AppCompatActivity {

    public static AssetManager mgr;
    public static String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/typefree/";
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

        int i = -1;
        for(Object suffix : file_suffixes.values()) {
            i++;
            if (!checked.contains(i)) {
                Log.i("APP", "skipping " + suffix + " " + i);
                continue;
            }
            File f = new File(Base.filesdir, fname + suffix);
            files.add(Uri.fromFile(f));
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
//            Log.i("APP", "Renamed file from " + from + " to " + to);
            from.renameTo(to);
        }
    }

    public String getFileDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd/MM/yyyy HH:mm", Locale.getDefault());
        Date now = new Date();
        String date = sdf.format(now);
        return date;
    }

    public static String sec_to_timestr(int dur) {
        ArrayList rests = new ArrayList();
        while (dur % 60 != 0) {
            rests.add(dur % 60);
            dur /= 60;
        }
        String[] times = {"h ", "m ", "s"};
        int start_idx = 3 - rests.size();
        String duration = "";
        for (int i = start_idx; i < 3; i++) {
            duration += String.valueOf(rests.get(2-i)) + times[i];  // -i to reverse
        }
        return duration;
    }

    public static int convertPixelsToDp(float px, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        int dp = (int) (px / (metrics.densityDpi / 160f));
        return dp;
    }
}


