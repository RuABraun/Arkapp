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
        recview.setLayoutManager(new LinearLayoutManager(act));
        recview.setAdapter(adapter);

        fviewmodel.getAllFiles().observe(act, new Observer<List<AFile>>() {
            @Override
            public void onChanged(@Nullable List<AFile> aFiles) {
                Collections.sort(aFiles, new Comparator<AFile>() {
                    @Override
                    public int compare(AFile lhs, AFile rhs) {
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE dd/MM/yyyy HH:mm", Locale.getDefault());
                        Date left_date = null;
                        try {
                            left_date = sdf.parse(lhs.date);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        Date right_date = null;
                        try {
                            right_date = sdf.parse(rhs.date);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return right_date.compareTo(left_date);
                    }
                });
                adapter.setData(aFiles);
            }
        });
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case 7:
                if (resultCode == RESULT_OK) {
                    Uri furi = data.getData();
                    String name = "";
                    String path = "";
                    boolean copied = false;
                    if (furi.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                        path = furi.getPath();
                        name = furi.getLastPathSegment();
                        Log.i("APP", "path " + path + " filename " + name);
                    } else if (furi.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                        copied = true;
                        Cursor retCursor = act.getContentResolver().query(furi, null, null, null, null);
                        retCursor.moveToFirst();
                        int idx_name = retCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        name = retCursor.getString(idx_name);
                        String[] split = name.split("\\.");
                        if (split.length == 2 && split[1].equals("wav")) {
                            path = Base.filesdir + split[0] + ".wav";
                            InputStream inputStream = null;
                            try {
                                inputStream = act.getContentResolver().openInputStream(furi);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            copyFileFromInputStream(inputStream, new File(path));
                            Log.i("APP", "name " + name + " path " + path);
                        }
                    } else {
                        Log.i("APP", "bla bla bla other scheme");
                    }
                    String[] split = name.split("\\.");
                    Log.i("APP", "name " + name + " splitlen " + split.length + " split1 " + split[1]);
                    if (split.length != 2 || !split[1].equals("wav")) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(act);
                        builder.setMessage("The file has to be in wav format! There are converters available online.");
                        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                        break;
                    }
                    MediaPlayer mPlayer = MediaPlayer.create(act.getApplicationContext(), Uri.parse(path));
                    int dur = (int) ((float)mPlayer.getDuration() / 1000.0f);
                    mPlayer.release();
                    String fname = split[0];
                    String fpath_new = Base.filesdir + fname + ".wav";
                    if (!copied) {
                        try {
                            copy(new File(path), new File(fpath_new));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    AFile afile = new AFile(fname, fname, dur, act.getFileDate());
                    act.f_repo.insert(afile);
                }
                break;
        }
    }

    public void on_add_press(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
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
