package ark.ark;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import java.util.concurrent.Semaphore;

public class Base extends AppCompatActivity {

    public static AssetManager mgr;
    public static String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Ark/";
    public static String filesdir = rootdir + "files/";
    public static String rmodeldir = rootdir + "model/";
    public static Semaphore available = new Semaphore(1);
    private long time_lastclick = 0;

    static {
        System.loadLibrary("rec-engine");
    }

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

    public void createRecEng(String modeldir) {
        Thread t = new Thread(new CreateRecEngRunnable(modeldir));
        t.setPriority(10);
        t.start();
    }

    public boolean is_spamclick() {
        if (SystemClock.elapsedRealtime() - time_lastclick < 500) {
            return true;
        }
        time_lastclick = SystemClock.elapsedRealtime();
        return false;
    }
}


