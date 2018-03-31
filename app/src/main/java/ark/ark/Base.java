package ark.ark;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class Base extends AppCompatActivity {

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
