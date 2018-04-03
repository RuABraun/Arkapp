package ark.ark;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

public class Base extends AppCompatActivity {

    public static AssetManager mgr;
    public static String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Ark/";
    public static String filesdir = rootdir + "files/";
    public static String modeldir = rootdir + "model/";

    public static native void load(AssetManager mgr, String modeldir);

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

}
