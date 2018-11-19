package ark.ark;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CpuUsageInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.Constraints;
import android.support.design.internal.BottomNavigationMenuView;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Base {

    public boolean is_recording = false;

    private String[] permissions = {Manifest.permission.RECORD_AUDIO,
                                     Manifest.permission.READ_EXTERNAL_STORAGE,
                                     Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int REQUEST_PERMISSIONS_CODE = 200;
    private static boolean perm_granted = false;
    private static List<String> mfiles = Arrays.asList("HCLG.fst", "final.mdl", "words.txt", "mfcc.conf", "align_lexicon.bin");
    Handler h_main = new Handler(Looper.getMainLooper());
    HandlerThread handlerThread;
    Handler h_background;
    Runnable title_runnable;
    Runnable runnable;
    Runnable trans_done_runnable;
    Runnable trans_edit_runnable;
    private FileRepository f_repo;
    private RecEngine recEngine;
    private EditText ed_transtext;
    private FloatingActionButton fab_rec;
    private FloatingActionButton fab_edit;
    private FloatingActionButton fab_copy;
    private FloatingActionButton fab_share;
    private EditText ed_title;
    private String fname_prefix = "";
    private String curr_cname = "";
    private boolean recognition_done = false;  // so we know if we have to modify a text file or rename files of conversation
    private AFile curr_afile;
    private String const_transcript = "";
    private ProgressBar spinner;
    final String PREFS_NAME = "MyPrefsFile";
    Thread t_del, t_stoptrans, t_starttrans;
    private boolean edited_title = false;  // we dont want to call afterTextChanged because we set the title (as happens at the start of recognition)
    private BottomNavigationView bottomNavigationView;
    private float ed_trans_to_edit_button_distance;
    private boolean is_editing = false, just_closed = false;
    private ProgressBar pb_init;
    private TextView tv_init;
    private int imageview_margin = 0;
    private ImageView img_view;

    static {
        System.loadLibrary("rec-engine");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (settings.getBoolean("is_first_time", true)) {
            pb_init.setVisibility(View.VISIBLE);
            tv_init.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.INVISIBLE);
            Log.i("APP", "Running for the first time.");
            t_del = new Thread(new Runnable() {
                @Override
                public void run() {
                    String[] dirs = {rmodeldir, filesdir};
                    for(String dir: dirs) {
                        File d = new File(dir);
                        File[] dirfiles = d.listFiles();
                        if (dirfiles != null) {
                            for (File fobj : dirfiles) {
                                fobj.delete();
                            }
                            d.delete();
                        }
                    }
                }
            });
            settings.edit().putBoolean("is_first_time", false).apply();
            t_del.setPriority(6);
            t_del.start();
        }
        switch (requestCode){
            case REQUEST_PERMISSIONS_CODE:
                for(int i=0; i < grantResults.length; i++) {
                    if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Required permissions not granted, app closing.", Toast.LENGTH_LONG).show();
                        finish();
                        break;
                    }
                }
                break;
        }
        perm_granted = true;
        onStart();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Handle permissions
        ArrayList<String> need_permissions = new ArrayList<>();
        for(String perm: permissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                need_permissions.add(perm);
            }
        }
        if (need_permissions.size() > 0) {
            ActivityCompat.requestPermissions(this,
                    need_permissions.toArray(new String[need_permissions.size()]),
                    REQUEST_PERMISSIONS_CODE);
        } else {
            perm_granted = true;
        }

        pb_init = findViewById(R.id.progressBar_init_setup);
        tv_init = findViewById(R.id.textview_init_setup);

        f_repo = new FileRepository(getApplication());

        spinner = findViewById(R.id.progressBar);
        ed_title = findViewById(R.id.ed_title);

        img_view = findViewById(R.id.imageView);

        final View activityRootView = findViewById(R.id.main_root_view);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                activityRootView.getWindowVisibleDisplayFrame(r);
                int height = activityRootView.getRootView().getHeight();
                int heightDiff = height - (r.bottom - r.top);
                if (heightDiff > height / 3.) {
                    ed_title.setCursorVisible(true);
                } else {
                    ed_title.setCursorVisible(false);
                }
            }
        });

        fab_rec = findViewById(R.id.button_rec);

        ed_transtext = findViewById(R.id.trans_edit_view);

        fab_rec.setVisibility(View.INVISIBLE);
        spinner.setVisibility(View.VISIBLE);

        fab_edit = findViewById(R.id.button_edit);
        fab_edit.setTranslationX(128f);

        fab_copy = findViewById(R.id.button_copy);
        fab_share = findViewById(R.id.button_share);
        fab_copy.setTranslationX(128f);
        fab_share.setTranslationX(128f);

        bottomNavigationView = findViewById(R.id.botNavig);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int item_id = item.getItemId();
                Intent intent;
                if (item_id == R.id.Manage) {
                    intent = new Intent(getApplicationContext(), Manage.class);
                    startActivity(intent);
                    return true;
                }
                if (item_id == R.id.Transcribe) {
                    return true;
                }
                if (item_id == R.id.Settings) {
                    intent = new Intent(getApplicationContext(), Settings.class);
                    startActivity(intent);
                    return true;
                }
                return true;
            }
        });
        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.botNavig);
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) bottomNavigationView.getChildAt(0);
        for (int i = 0; i < menuView.getChildCount(); i++) {
            final View iconView = menuView.getChildAt(i).findViewById(android.support.design.R.id.icon);
            final ViewGroup.LayoutParams layoutParams = iconView.getLayoutParams();
            final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            layoutParams.height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, displayMetrics);
            layoutParams.width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, displayMetrics);
            iconView.setLayoutParams(layoutParams);
        }
    }

    private void do_setup() {
        try {
            if (t_del != null) {
                t_del.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i("STORAGE", rootdir);
        File f = new File(rmodeldir);
        if (!f.exists()) f.mkdirs();
        File f2 = new File(filesdir);
        if (!f2.exists()) f2.mkdirs();
        boolean all_exist = true;
        for(String fname: mfiles) {
            File mf = new File(rmodeldir + fname);
            if (!mf.exists()) all_exist = false;
        }
        if (!all_exist) {
            Log.i("APP", "before getasset");
            mgr = getResources().getAssets();
            Log.i("APP", "extracting");
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    native_load(mgr, rmodeldir);
                    h_main.post(new Runnable() {
                        @Override
                        public void run() {
                            spinner.setVisibility(View.VISIBLE);
                            pb_init.setVisibility(View.GONE);
                            tv_init.setVisibility(View.GONE);
                        }
                    });
                    recEngine = RecEngine.getInstance(rmodeldir);
                }
            });
            t.setPriority(6);
            t.start();
        } else {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    recEngine = RecEngine.getInstance(rmodeldir);
                }
            });
            t.setPriority(6);
            t.start();
        }

        /*try {
            casemodel = new Interpreter(loadModelFile());
            if (casemodel == null) {
                Log.e("APP", "MODEL WAS NOT LOADED");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }


    @Override
    public void onStart() {
        super.onStart();

        if (!perm_granted) {
            return;
        }

        do_setup();
        h_main.postDelayed(new Runnable() {
            public void run() {
                runnable=this;
                h_main.postDelayed(runnable, 100);
                if (RecEngine.isready) {
                    fab_rec.setVisibility(View.VISIBLE);
                    spinner.setVisibility(View.INVISIBLE);
                    h_main.removeCallbacks(runnable);
                }
            }
        }, 100);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenHeight = dm.heightPixels;

        Log.i("APP", "height " + screenHeight + " " + dm.density);
        ed_trans_to_edit_button_distance = (screenHeight / dm.density);
    }


    @Override
    protected void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("BackgroundHandlerThread");
        handlerThread.start();
        h_background = new Handler(handlerThread.getLooper());
        ed_title.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(final Editable s) {
                curr_cname = s.toString().replaceAll("(^\\s+|\\s+$)", "");
                if (curr_cname.equals("")) {
                    curr_cname = getString(R.string.default_convname);
                }
                fname_prefix = getFileName(curr_cname, f_repo);
                h_main.removeCallbacks(title_runnable);
                if (recognition_done && !edited_title) {
                    title_runnable = new Runnable() {
                        @Override
                        public void run() {
                            Log.i("APP", "fname " + fname_prefix + " cname " + curr_cname);
                            f_repo.rename(curr_afile, curr_cname, fname_prefix);
                        }
                    };
                    h_main.postDelayed(title_runnable, 500);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(ed_transtext.getWindowToken(), 0);
        super.onPause();
        if (title_runnable != null) {
            h_main.removeCallbacks(title_runnable);
            title_runnable.run();
        }
        if (trans_edit_runnable != null) {
            h_background.removeCallbacks(trans_edit_runnable);
            trans_edit_runnable.run();
        }
        handlerThread.quit();
    }

    @Override
    protected void onDestroy() {
        Log.i("APP", "Destroying");
        recEngine.delete();
//        casemodel.close();
        super.onDestroy();
    }

    public void stop_transcribe() {
        long num_out_frames = recEngine.stop_trans_stream();
        if (curr_cname.equals("")) {
            curr_cname = getString(R.string.default_convname);
        }

//        doInference();

        String date = getFileDate();
        String title = curr_cname;
        String fname = getFileName(curr_cname, f_repo);
        fname_prefix = fname;

        renameConv("tmpfile", fname);

        int duration_s = (int) (3 * num_out_frames) / 100;
        AFile afile = new AFile(title, fname, duration_s, date);
        long id = f_repo.insert(afile);
        curr_afile = f_repo.getById(id);  // has correct ID
        Log.i("APP", "FILE ID " + curr_afile.getId() + " title: " + title + " fname: " + fname);
        h_main.removeCallbacks(runnable);
        h_main.post(new Runnable() {
                        @Override
                        public void run() {
                            update_text();
                            fab_edit.animate().translationX(0f);
                            fab_copy.animate().translationX(0f);
                            fab_share.animate().translationX(0f);
                        }
        });
        recognition_done = true;
    }

    public void record_switch(View view) {
        if (is_spamclick()) return;

        Log.i("APP", "isrecording: " + String.valueOf(is_recording));
        if (!is_recording ) {
            if (!RecEngine.isready) return;
            try {
                if (t_stoptrans != null) t_stoptrans.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (recognition_done || curr_cname.equals("")) {
                curr_cname = getString(R.string.default_convname);
                edited_title = true;
                ed_title.setText(curr_cname, TextView.BufferType.EDITABLE);
                edited_title = false;
            }
            if (recognition_done) const_transcript = "";

            if (trans_edit_runnable != null) {
                h_background.removeCallbacks(trans_edit_runnable);
                h_background.post(trans_edit_runnable);
            }

            recognition_done = false;
            fab_edit.animate().translationX(128f);
            fab_copy.animate().translationX(128f);
            fab_share.animate().translationX(128f);
            float offset = (float) fab_edit.getLeft() - fab_rec.getLeft() - 4;
            fab_rec.animate().translationX(offset);
            fab_rec.setImageResource(R.drawable.stop);

            ed_transtext.setText(const_transcript, TextView.BufferType.EDITABLE);
            final String fpath = filesdir + "tmpfile";
            t_starttrans = new Thread(new Runnable() {
                @Override
                public void run() {
                    recEngine.transcribe_stream(fpath);
                }
            });
            t_starttrans.setPriority(7);
            t_starttrans.start();

            is_recording = true;

            h_main.postDelayed(new Runnable() {
                public void run() {
                    runnable=this;
                    update_text();
                    h_main.postDelayed(runnable, 25);
                }
            }, 25);

        } else {
            spinner.setVisibility(View.VISIBLE);
            is_recording = false;

            h_main.postDelayed(new Runnable() {
                public void run() {
                    trans_done_runnable=this;
                    h_main.postDelayed(trans_done_runnable, 100);
                    if (recognition_done) {
                        fab_rec.animate().translationX(0f);
                        fab_rec.setImageResource(R.drawable.mic_full_inv);
                        spinner.setVisibility(View.INVISIBLE);
                        h_main.removeCallbacks(trans_done_runnable);
                    }
                }
            }, 100);

            t_stoptrans = new Thread(new Runnable() {
                @Override
                public void run() {
                    stop_transcribe();
                    Log.i("APP", "Finished recording.");
                }
            });
            t_stoptrans.setPriority(7);
            t_stoptrans.start();

        }
    }

    public static String getFileName(String cname, final FileRepository f_repo_) {
        final AtomicInteger fcount = new AtomicInteger();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int num = f_repo_.getNumFiles();
                fcount.set(num);
            }
        });
        t.setPriority(10);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int cnt = fcount.get() + 1;
        cname = cname.replace(' ', '_') + "_";
        String fname = cname + Integer.toString(cnt);
        String wavpath = fname + ".wav";
        while (new File(wavpath).exists()) {
            cnt++;
            fname = cname + Integer.toString(cnt);
            wavpath = fname + ".wav";
        }
        return fname;
    }

    public void update_text() {
        String str = recEngine.get_text();
        String conststr = recEngine.get_const_text();
        String all = conststr + str;
        ed_transtext.setText(all, TextView.BufferType.EDITABLE);
        ed_transtext.setSelection(all.length());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        View v = getCurrentFocus();
        boolean ret = super.dispatchTouchEvent(event);
        if (v == ed_transtext) {
            View w = getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];
            int r = fab_edit.getLeft();
//            Log.d("APP", "Touch event " + x + " right " + w.getRight() + " fabedit left " + r);
            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom()) && is_editing) {
                Log.i("APP", "in if of dispatch");
                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view = this.getCurrentFocus();
                if (view == null) {
                    view = new View(this);
                }
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                fab_edit.animate().translationY(0.f);
                fab_share.setVisibility(View.VISIBLE);
                fab_copy.setVisibility(View.VISIBLE);
                ed_transtext.clearFocus();
                ed_transtext.setFocusableInTouchMode(false);
                is_editing = false;
                just_closed = true;
            }
        } else if (v == ed_title) {
            View w = getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];
            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom())) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view = this.getCurrentFocus();
                if (view == null) {
                    view = new View(this);
                }
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
        return ret;
    }

    public boolean checkRecDone() {
        if (!recognition_done) {
            Toast.makeText(getApplicationContext(), "You have to transcribe something first!", Toast.LENGTH_SHORT).show();
            return false;
        } else return true;
    }

    public void onCopyClick(View view) {
        try {
            if (t_stoptrans != null) t_stoptrans.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!checkRecDone()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String text = ed_transtext.getText().toString();
        ClipData clip = ClipData.newPlainText("Transcript", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getApplicationContext(), "Copied", Toast.LENGTH_SHORT).show();
    }

    public void onShareClick(View view) {
        try {
            if (t_stoptrans != null) t_stoptrans.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!checkRecDone()) return;
        ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(fname_prefix);
        dialog.show(getSupportFragmentManager(), "ShareDialog");
    }

    public void onEditClick(View view) {
        if (is_spamclick()) return;
        ed_transtext.setFocusable(true);
        ed_transtext.setFocusableInTouchMode(true);

        if (!is_editing && !just_closed) {
            is_editing = true;
            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ed_transtext, InputMethodManager.SHOW_IMPLICIT);
            fab_edit.setImageResource(R.drawable.done);
            fab_rec.setVisibility(View.INVISIBLE);
            Log.i("APP", "IN TRANSEDIT EDIT PRESS " + ed_trans_to_edit_button_distance);
            fab_edit.animate().translationY(-ed_trans_to_edit_button_distance);
            fab_share.setVisibility(View.INVISIBLE);
            fab_copy.setVisibility(View.INVISIBLE);

            ed_transtext.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(final Editable s) {
                    final String text = s.toString().replaceAll("(^\\s+|\\s+$)", "");

                    h_background.removeCallbacks(trans_edit_runnable);
                    trans_edit_runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FileWriter fw = new FileWriter(new File(filesdir + curr_afile.fname + file_suffixes.get("text")), false);
                                fw.write(text);
                                fw.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    h_background.postDelayed(trans_edit_runnable, 500);
                }
            });

            ConstraintLayout.LayoutParams lay_params = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
            imageview_margin = lay_params.bottomMargin;
            int n = convertPixelsToDp((float) imageview_margin * 9, getApplicationContext());
            lay_params.bottomMargin = n;

            ed_transtext.invalidate();
            ed_transtext.requestLayout();
        } else {
            just_closed = false;
            Log.i("APP", "DONE EDIT");
            h_background.removeCallbacks(trans_edit_runnable);
            fab_edit.setImageResource(R.drawable.edit);
            fab_rec.setVisibility(View.VISIBLE);
            is_editing = false;
            ConstraintLayout.LayoutParams lay_params = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
            lay_params.bottomMargin = imageview_margin;
            ed_transtext.invalidate();
            ed_transtext.requestLayout();
            // everything already done in dispatchTouchEvent
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

//
//    public void doInference() {
//        int[] input = new int[1];
//        Arrays.fill(input, 1);
//        float[] state = new float[256];
//        Arrays.fill(state, 0f);
//
//        Map<Integer, Object> map_of_indices_to_outputs = new HashMap<>();
//        float[][] prob = new float[1][3];
//        map_of_indices_to_outputs.put(casemodel.getOutputIndex("output_prob"), prob);
//
//        float[][] newstate = new float[1][256];
//        map_of_indices_to_outputs.put(casemodel.getOutputIndex("output_state"), newstate);
//
//        Object[] inputs = {input, state};
//        casemodel.runForMultipleInputsOutputs(inputs, map_of_indices_to_outputs);
//        int idx = casemodel.getOutputIndex("output_prob");
//        float val = ((float[][]) map_of_indices_to_outputs.get(idx))[0][0];
//        Log.i("APP", "value: " + val);
//    }

//    private MappedByteBuffer loadModelFile() throws IOException {
//        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model/tf_model.tflite");
//        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = fileDescriptor.getStartOffset();
//        long declaredLength = fileDescriptor.getDeclaredLength();
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//    }

}


