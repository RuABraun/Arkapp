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

public class Base extends AppCompatActivity {

    public static AssetManager mgr;
    public static String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Ark/";
    public static String filesdir = rootdir + "files/";
    public static String rmodeldir = rootdir + "model/";
    public static ArrayList<String> file_suffixes = new ArrayList<String>(Arrays.asList(".txt", ".wav", "_timed.txt"));
    private long time_lastclick = 0;

    public native void native_load(AssetManager mgr, String rmodeldir);

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        Intent intent;
        switch (item.getItemId()) {
            case R.id.Main:
                intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                return true;
            case R.id.Manage:
                intent = new Intent(this, Manage.class);
                startActivity(intent);
                return true;
            case R.id.Settings:
                intent = new Intent(this, Settings.class);
                startActivity(intent);
                return true;
            case R.id.Help:
                intent = new Intent(this, Help.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

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

        for(int i = 0; i < num; i++) {
            if (!checked.contains(i)) continue;
            String suffix = Base.file_suffixes.get(i);
            File f = new File(Base.filesdir, fname + suffix);
            files.add(Uri.fromFile(f));
        }
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share file(s)"));
    }

    public static void renameConv(String fname_old, String fname) {
        File from, to;
        for (int i = 0; i < file_suffixes.size(); i++) {
            String suffix = file_suffixes.get(i);
            from = new File(filesdir + fname_old + suffix);
            to = new File(filesdir + fname + suffix);
            from.renameTo(to);
        }
    }
}


