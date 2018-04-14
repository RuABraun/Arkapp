package ark.ark;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;


public class Manage extends Base {

    private long time_lastclick = 0;
    MediaPlayer mp = null;
    ListView lv = null;
    ArrayAdapter<String> lv_adapt = null;
    ArrayList<String> files = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage);
        this.setTitle("Ark - Files");

        File dir = new File(filesdir);
        File[] fpaths = dir.listFiles();
        Arrays.sort(fpaths);
        files = new ArrayList<String>();
        Log.i("MANAGE CREATE", Integer.toString(fpaths.length));
        for(int i=0;i<fpaths.length;i++) {
            files.add(fpaths[i].getName());
        }

        lv = findViewById(R.id.filesview);
        lv.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        lv_adapt = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, files);
        lv.setAdapter(lv_adapt);

    }

    public void play_switch(View view) {
        if (SystemClock.elapsedRealtime() - time_lastclick < 500) {
            return;
        }

        ArrayList<String> fpaths = get_sel_files();
        for (String fpath : fpaths) {
            Uri wavfile = Uri.parse(fpath);
            mp = MediaPlayer.create(this, wavfile);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }
            });
            mp.start();
        }
    }

    public void transcribe_switch(View view) {
        if (SystemClock.elapsedRealtime() - time_lastclick < 500) {
            return;
        }

        ArrayList<String> fpaths = get_sel_files();
        for (String fpath : fpaths) {
            RecEngine.transcribe(fpath, modeldir, filesdir + "out.txt");
        }
    }

    public void delete_switch(View view) {
        Log.i("APP", "Num items " + Integer.toString(lv.getCount()));
        ArrayList<String> fpaths = get_sel_files();
        for(String fpath: fpaths) {
            File fdel = new File(fpath);
            fdel.delete();
        }
        uncheck_all();
        Log.i("APP", "Num items " + Integer.toString(lv.getCount()));
        list_reset();
        Log.i("APP", "Num items " + Integer.toString(lv.getCount()));
    }

    public ArrayList<String> get_sel_files() {  // get selected files
        Log.i("get_sel_items", "Num items " + Integer.toString(lv.getCount()));
        SparseBooleanArray spa = lv.getCheckedItemPositions();
        ArrayList<String> ret = new ArrayList<>();
        for(int i=0; i < spa.size(); i++) {
            boolean boolvar = spa.valueAt(i);
            Log.i("APP", Boolean.toString(boolvar));
            String tmp = filesdir + ((TextView) lv.getChildAt(spa.keyAt(i))).getText().toString();
            Log.i("APP", tmp);
            if (boolvar) {
                ret.add(tmp);
            }
        }
        return ret;
    }

    public void uncheck_all() {
        for(int i=0; i < lv.getCount(); i++) {
            lv.setItemChecked(i, false);
        }
    }

    public void list_reset() {
        files.clear();
        File dir = new File(filesdir);
        File[] fpaths = dir.listFiles();
        Arrays.sort(fpaths);
        ArrayList<String> tmp = new ArrayList<String>();
        Log.i("MANAGE CREATE", Integer.toString(fpaths.length));
        for(int i=0;i<fpaths.length;i++) {
            tmp.add(fpaths[i].getName());
        }
        files.addAll(tmp);
        lv_adapt.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        lv = null;
        lv_adapt = null;
        if (mp != null) {
            mp.release();
            mp = null;
        }
    }
}
