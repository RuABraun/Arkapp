package ark.ark;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;



public class Manage extends Base {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage);
        this.setTitle("Ark - Files");

        File dir = new File(getString(R.string.SaveDir));
        File[] fpaths = dir.listFiles();
        ArrayList<String> files = new ArrayList<String>();
        for(int i=0;i<fpaths.length;i++) {
            files.add(fpaths[i].getName());
        }

        ListView lv = findViewById(R.id.filesview);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, files);
        lv.setAdapter(arrayAdapter);
    }
}
