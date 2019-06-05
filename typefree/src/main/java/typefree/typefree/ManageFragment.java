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
import android.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;


public class ManageFragment extends Fragment {

    MyRecyclerAdapter adapter;
    private FileViewModel fviewmodel;
    private MainActivity act;
    private ProgressBar pb;
    private Runnable runnable;
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
        pb = view.findViewById(R.id.load_manage);
        RecyclerView recview = view.findViewById(R.id.rv_files);
        recview.setLayoutManager(new LinearLayoutManager(act));
        recview.setAdapter(adapter);
        recview.setHasFixedSize(true);
        fviewmodel.getAllFiles().observe(act, new Observer<List<AFile>>() {
            @Override
            public void onChanged(@Nullable List<AFile> aFiles) {
                adapter.setData(aFiles);
                act.h_main.post(new Runnable() {
                    @Override
                    public void run() {
                        runnable = this;
                        if (adapter.init_done) {
                            pb.setVisibility(View.INVISIBLE);
                        } else {
                            act.h_main.postDelayed(runnable, 50);
                        }
                    }
                });
            }
        });
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    class ConvertAudioRunnable implements Runnable {
        String fname;
        String inpath;
        String outpath;
        ConvertAudioRunnable(String f, String inp, String outp) {
            fname = f;
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
                        builder.setMessage("Could not include this file :( Are you sure this is an audio file?");
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
            MediaPlayer mPlayer = MediaPlayer.create(act.getApplicationContext(), Uri.parse(outpath));
            int dur = (int) ((float) mPlayer.getDuration() / 1000.0f);
            mPlayer.release();
            AFile afile = new AFile(fname, fname, dur, act.getFileDate());
            act.f_repo.insert(afile);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case 7:
                if (resultCode == RESULT_OK) {
                    Uri furi = data.getData();
                    String name = "";
                    String path = "";
                    String newpath = "";
                    String fname_tmp = "";
                    boolean copied = false;
                    if (furi.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                        path = furi.getPath();
                        name = furi.getLastPathSegment();
                        Log.i("APP", "bla bla path " + path + " filename " + name);
                    } else if (furi.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                        copied = true;

                        Cursor retCursor = act.getContentResolver().query(furi, null, null, null, null);
                        retCursor.moveToFirst();
                        int idx_name = retCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        name = retCursor.getString(idx_name);
                        String[] split = name.split("\\.");
                        path = Base.filesdir + "tmp." + split[split.length-1];
                        Log.i("APP", "bla bla path " + path + " filename " + name);
                        InputStream inputStream = null;
                        try {
                            inputStream = act.getContentResolver().openInputStream(furi);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                        copyFileFromInputStream(inputStream, new File(path));

                        fname_tmp = split[0];
                        for (int i = 1; i < split.length - 1; i++) {
                            fname_tmp += split[i];
                        }
                        newpath = Base.filesdir + fname_tmp + ".wav";

                    } else {
                        Log.i("APP", "bla bla bla other scheme");
                    }

                    if (newpath.equals("")) {
                        Log.e("APP", "Newpath is empty!");
                        return;
                    }
                    final String inpath = path;
                    final String outpath = newpath;
                    final String fname = fname_tmp;

                    r = new ConvertAudioRunnable(fname, inpath, outpath);
                    new Thread(r).start();
                    //act.h_background.post(new );

                    Log.i("APP-MANAGEFRAG", "Done");
                    break;
                }

        }
    }

    public void on_add_press(View view) {
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
