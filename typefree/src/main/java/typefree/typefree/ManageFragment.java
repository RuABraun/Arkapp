package typefree.typefree;


import android.app.AlertDialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bugsnag.android.Bugsnag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.app.Activity.RESULT_OK;


public class ManageFragment extends Fragment {

    MyRecyclerAdapter adapter;
    private FileViewModel fviewmodel;
    private MainActivity act;
    private Runnable runnable;
    private Observer<List<AFile>> observer;
    private FloatingActionButton fab_import;
    private ProgressBar pb_import;
    Runnable r;

    public ManageFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act = (MainActivity) getActivity();
        fviewmodel = ViewModelProviders.of(act).get(FileViewModel.class);
        adapter = new MyRecyclerAdapter(act, act.f_repo, act.fragmentManager);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage, container, false);
        RecyclerView recview = view.findViewById(R.id.rv_files);
        fab_import = view.findViewById(R.id.button_add);
        pb_import = view.findViewById(R.id.progbar_import);
        recview.setLayoutManager(new LinearLayoutManager(act));
        recview.setAdapter(adapter);

        if (!act.just_imported_file) {
            String tag = "knows_can_import";
            if (!act.settings.getBoolean(tag, false)) {
                String msg = "Use the + button to import audio or video files from your phone.";
                TipDialog dialog = TipDialog.newInstance("Tip!", msg, tag);
                dialog.show(act.fragmentManager, "TipDialog");
            }
        }
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Bugsnag.leaveBreadcrumb("In ManageFragment onStart()");
        observer = new Observer<List<AFile>>() {
            @Override
            public void onChanged(@Nullable List<AFile> aFiles) {
                adapter.setData(aFiles);
            }
        };
        fviewmodel.getAllFiles().observe(act, observer);
        if (!act.just_imported_file) {
            pb_import.setVisibility(View.INVISIBLE);
        } else {
            pb_import.setVisibility(View.VISIBLE);
            runnable = new Runnable() {
                @Override
                public void run() {
                    if (act.finished_conversion) {
                        act.finished_conversion = false;
                        pb_import.setVisibility(View.INVISIBLE);
                    } else {
                        act.h_main.postDelayed(this, 500);
                    }
                }
            };
            act.h_main.post(runnable);
        }
    }

    @Override
    public void onStop() {
        fviewmodel.getAllFiles().removeObserver(observer);
        super.onStop();
    }

    @Override
    public void onPause() {
        act.h_main.removeCallbacks(runnable);
        pb_import.setVisibility(View.INVISIBLE);
        super.onPause();
    }

    class ConvertAudioRunnable implements Runnable {
        String inpath;
        String outpath;
        ConvertAudioRunnable(String inp, String outp) {
            inpath = inp;
            outpath = outp;
        }
        public void run() {
            int ret = act.recEngine.convert_audio(inpath, outpath);
            if (ret != 0) {
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(act);
                        builder.setMessage("Could not include this file :( Are you sure this is an audio file?\n\nGet in touch if you think this should work: contact@typefree.io");
                        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                });
                return;
            }
            File f = new File(outpath);
            MediaPlayer mPlayer = MediaPlayer.create(act.getApplicationContext(), Uri.fromFile(f));
            int dur = (int) ((float) mPlayer.getDuration() / 1000.0f);
            mPlayer.stop();
            mPlayer.release();
            String fname = f.getName();
            String[] split = fname.split("\\.");
            String basename = split[0];
            AFile afile = new AFile(basename, basename, dur, act.getFileDate());
            act.f_repo.insert(afile);
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Bugsnag.leaveBreadcrumb("Finished importing file.");
                    act.just_imported_file = false;
                    String tag = "knows_transcribe_after_import";
                    if (!act.settings.getBoolean(tag, false)) {
                        String msg = "Now press the microphone on the file you added to transcibe it.";
                        TipDialog dialog = TipDialog.newInstance("Tip!", msg, tag);
                        dialog.show(act.fragmentManager, "TipDialog");
                    }
                }
            });

            act.finished_conversion = true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case 7:
                if (resultCode == RESULT_OK) {
                    Uri furi = data.getData();
                    String path = "";
                    String newpath = "";
                    if (furi.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                        path = furi.getPath();
                        File f = new File(path);
                        String wholename = f.getName();
                        String[] split = wholename.split("\\.");
                        String basename = split[0];
                        for (int i = 1; i < split.length - 1; i++) {
                            basename += split[i];
                        }
                        basename = MainActivity.getFileName(basename, act.f_repo);
                        newpath = Base.filesdir + basename + ".wav";
                        Log.i("APP", "SCHEME_FILE: path " + path + " newpath " + newpath);
                    } else if (furi.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {

                        Cursor retCursor = act.getContentResolver().query(furi, null, null, null, null);
                        retCursor.moveToFirst();
                        int idx_name = retCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        String name = retCursor.getString(idx_name);
                        String[] split = name.split("\\.");
                        String suffix = split[split.length-1];
                        path = Base.filesdir + "tmp." + suffix;
                        InputStream inputStream = null;
                        try {
                            inputStream = act.getContentResolver().openInputStream(furi);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                        copyFileFromInputStream(inputStream, new File(path));

                        String basename = split[0];
                        for (int i = 1; i < split.length - 1; i++) {
                            basename += split[i];
                        }
                        basename = MainActivity.getFileName(basename, act.f_repo);
                        newpath = Base.filesdir + basename + ".wav";
                        Log.i("APP", "SCHEME_CONTENT: path " + path + " newpath " + newpath);
                    } else {
                        Log.e("APP", "Other scheme!");
                    }

                    if (newpath.equals("")) {
                        Log.e("APP", "Newpath is empty!");
                        Bugsnag.leaveBreadcrumb("Newpath is empty!");
                        Bugsnag.notify(new RuntimeException());
                        return;
                    }
                    final String inpath = path;
                    final String outpath = newpath;
                    r = new ConvertAudioRunnable(inpath, outpath);
                    act.finished_conversion = false;
                    new Thread(r).start();
                    Bugsnag.leaveBreadcrumb("Reached end of importing code.");
                    break;
                }
            default:
                Log.e("APP", "Different request code?.");
                Bugsnag.leaveBreadcrumb("Different request code? " + requestCode);
        }
    }

    public void on_add_press(View view) {
        Bugsnag.leaveBreadcrumb("Importing file.");
        act.just_imported_file = true;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, 7);
    }

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    public boolean copyFileFromInputStream(InputStream inputStream, File dest) {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(dest);
            final byte[] buffer = new byte[64 * 1024]; // 64KB
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }

                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                return false;
            }
        }
    }

}
