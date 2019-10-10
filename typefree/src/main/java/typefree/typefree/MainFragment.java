package typefree.typefree;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bugsnag.android.Bugsnag;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static typefree.typefree.Base.filesdir;


public class MainFragment extends Fragment {


    public boolean is_recording = false;
    Runnable title_runnable, runnable, trans_update_runnable, trans_done_runnable,
        trans_edit_runnable, performance_runnable;
    private EditText ed_transtext;
    private FloatingActionButton fab_rec, fab_edit, fab_copy, fab_share, fab_del, fab_pause;
    private EditText ed_title;
    private String fname_prefix = "";
    private String curr_cname = "";
    private boolean recognition_done = false;  // so we know if we have to modify a text file or rename files of conversation
    private AFile curr_afile;
    private int const_trans_size = 0;
    private ProgressBar spinner;
    Thread t_stoptrans, t_starttrans, t_perf_adjuster;
    private boolean manually_editing_title = false;  // we dont want to call afterTextChanged because we set the title (as happens at the start of recognition)
    private boolean editing_transtext = false;
    private TextView tv_counter, tv_transcribe_hint;
    private ImageView img_view;
    private TextWatcher title_textWatcher, text_textWatcher;
    private MainActivity act;
    private View fview;
    private StringBuilder trans_text;
    private boolean paused_stream = false;
    Timer time_counter;
    int fab_rec_botmargin;

    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act = (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        Log.i("APP", "Starting MainFragment onCreateView");
        tv_counter = view.findViewById(R.id.tv_counter);
        tv_counter.setVisibility(View.INVISIBLE);
        tv_transcribe_hint = view.findViewById(R.id.tv_transcribe_hint);

        spinner = view.findViewById(R.id.progressBar);
        ed_title = view.findViewById(R.id.ed_title);

        img_view = view.findViewById(R.id.imageView);

        final View activityRootView = view.findViewById(R.id.main_fragment);
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

        fab_rec = view.findViewById(R.id.button_rec);

        ed_transtext = view.findViewById(R.id.trans_edit_view);

        fab_rec.hide();
        spinner.setVisibility(View.VISIBLE);

        fab_edit = view.findViewById(R.id.button_edit);
        fab_edit.setTranslationX(256f);

        fab_copy = view.findViewById(R.id.button_copy);
        fab_share = view.findViewById(R.id.button_share);
        fab_del = view.findViewById(R.id.button_delete);
        fab_pause = view.findViewById(R.id.button_pause);
        fab_copy.setTranslationX(256f);
        fab_share.setTranslationX(256f);
        fab_del.setTranslationX(-256f);

        this.fview = view;
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i("APP", "Starting main fragment");
        Bugsnag.leaveBreadcrumb("In MainFragment onStart");
        act.h_main.postDelayed(new Runnable() {
            public void run() {
                runnable=this;
                act.h_main.postDelayed(runnable, 100);
                if (RecEngine.isready) {
                    fab_rec.show();
                    spinner.setVisibility(View.GONE);
                    act.h_main.removeCallbacks(runnable);
                }
            }
        }, 100);

        if (act.settings.getBoolean("knows_mic_location", true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(act);
            builder.setTitle("Tip!")
                    .setMessage("For best performance point the microphone towards the speaker.\n\nTypically the mic is at the bottom of the phone.")
                    .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
            TextView tv = (TextView) alert.findViewById(android.R.id.message);
            tv.setTextSize(18);
            act.settings.edit().putBoolean("knows_mic_location", false).apply();
        }

        trans_text = new StringBuilder();
        trans_text.ensureCapacity(128);
    }


    @Override
    public void onResume() {
        super.onResume();
        if (title_textWatcher == null) {
            title_textWatcher = new TextWatcher() {
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

                    fname_prefix = MainActivity.getFileName(curr_cname, act.f_repo);
                    act.h_background.removeCallbacks(title_runnable);
                    if (recognition_done && !manually_editing_title) {
                        title_runnable = new Runnable() {
                            @Override
                            public void run() {
                                Log.i("APP", "Renaming " + curr_afile.getId() + " from " + curr_afile.fname + " to " + fname_prefix);
                                act.f_repo.rename(curr_afile, curr_cname, fname_prefix);
                                curr_afile.title = curr_cname;
                                curr_afile.fname = fname_prefix;
                            }
                        };
                        act.h_background.postDelayed(title_runnable, 500);
                    }
                }


            };
            ed_title.addTextChangedListener(title_textWatcher);
        }
    }

    @Override
    public void onPause() {
        Bugsnag.leaveBreadcrumb("Mainfragment onPause");
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(ed_transtext.getWindowToken(), 0);
        stop_transcribe(getView());
        if (title_runnable != null) {
            act.h_background.removeCallbacks(title_runnable);
            title_runnable.run();
        }
        if (trans_edit_runnable != null && curr_afile != null) {
            act.h_background.removeCallbacks(trans_edit_runnable);
            trans_edit_runnable.run();
        }
        if (trans_update_runnable != null) {
            act.h_main.removeCallbacks(trans_update_runnable);
        }
        ed_title.removeTextChangedListener(title_textWatcher);
        super.onPause();
    }


    public void stop_transcribe(View view) {
        if (!is_recording) return;
        Bugsnag.leaveBreadcrumb("Stopped transcribing stream.");
        time_counter.cancel();
        act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (paused_stream) pause_switch(view);
        fab_pause.setVisibility(View.GONE);
        paused_stream = false;
        spinner.setVisibility(View.VISIBLE);
        is_recording = false;
        RecEngine.isrunning = false;
        t_perf_adjuster.interrupt();
        if (act.pm.isSustainedPerformanceModeSupported()) {
            Log.i("APP", "Turning sustainedperf off for good");
            act.getWindow().setSustainedPerformanceMode(false);
        }

        act.h_main.postDelayed(new Runnable() {
            public void run() {
                trans_done_runnable=this;
                if (recognition_done) {
                    fab_rec.animate().translationX(0f);
                    fab_rec.setImageDrawable(act.getDrawable(R.drawable.mic_full_inv));
                    spinner.setVisibility(View.GONE);
                    act.h_main.removeCallbacks(trans_done_runnable);
                    fab_edit.animate().translationX(0f);
                    fab_copy.animate().translationX(0f);
                    fab_share.animate().translationX(0f);
                    fab_del.animate().translationX(0f);
                    tv_transcribe_hint.setVisibility(View.VISIBLE);
                    update_text();
                    tv_counter.setVisibility(View.INVISIBLE);
                    act.bottomNavigationView.setVisibility(View.VISIBLE);
                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) fab_rec.getLayoutParams();
                    Log.i("APP", "use margin " + fab_rec_botmargin + " old " + layoutParams.bottomMargin);
                    layoutParams.bottomMargin = fab_rec_botmargin;
                    fab_rec.invalidate();
                    fab_rec.requestLayout();
                } else {
                    act.h_main.postDelayed(trans_done_runnable, 100);
                }
            }
        }, 100);

        t_stoptrans = new Thread(new Runnable() {
            @Override
            public void run() {
                long num_out_frames = act.recEngine.stop_trans_stream();
                if (curr_cname.equals("")) {
                    curr_cname = getString(R.string.default_convname);
                }
                act.h_main.removeCallbacks(trans_update_runnable);
                act.h_main.post(new Runnable() {
                    @Override
                    public void run() {
                        update_text();
                    }
                });

                String date = act.getFileDate();
                String title = curr_cname;
                String fname = MainActivity.getFileName(curr_cname, act.f_repo);
                fname_prefix = fname;

                MainActivity.renameConv("tmpfile", fname);

                int duration_s = (int) (3 * num_out_frames) / 100;
                AFile afile = new AFile(title, fname, duration_s, date);
                long id = act.f_repo.insert(afile);
                curr_afile = act.f_repo.getById(id);  // has correct ID
                Log.i("APP", "FILE ID " + curr_afile.getId() + " title: " + title + " fname: " + fname);
                recognition_done = true;
                Log.i("APP", "Finished recording.");
            }
        });
        t_stoptrans.setPriority(8);
        t_stoptrans.start();
    }

    public void record_switch(View view) {
        if (!is_recording ) {
            Bugsnag.leaveBreadcrumb("Started transcribing stream.");
            tv_transcribe_hint.setVisibility(View.INVISIBLE);
            if (!RecEngine.isready) return;
            try {
                if (t_stoptrans != null) t_stoptrans.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (trans_edit_runnable != null) {
                act.h_background.removeCallbacks(trans_edit_runnable);
                act.h_background.post(trans_edit_runnable);
            }

            if (recognition_done || curr_cname.equals("")) {
                curr_cname = getString(R.string.default_convname);
                manually_editing_title = true;
                ed_title.setText(curr_cname);
                manually_editing_title = false;
            }

            recognition_done = false;
            fab_edit.animate().translationX(256f);
            fab_copy.animate().translationX(256f);
            fab_share.animate().translationX(256f);
            fab_del.animate().translationX(-256f);
            float offset = (float) fab_edit.getLeft() - fab_rec.getLeft() - 4;

            fab_rec.setImageDrawable(act.getDrawable(R.drawable.stop));
            fab_rec.animate().translationX(offset);
            ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) fab_rec.getLayoutParams();
            fab_rec_botmargin = layoutParams.bottomMargin;
            Log.i("APP", "set margin " + fab_rec_botmargin);
            layoutParams.bottomMargin = layoutParams.bottomMargin + act.bottomNavigationView.getMeasuredHeight();

            ed_transtext.setText("");
            const_trans_size = 0;
            trans_text.setLength(0);

            final String fpath = filesdir + "tmpfile";
            t_starttrans = new Thread(new Runnable() {
                @Override
                public void run() {
                    act.recEngine.transcribe_stream(fpath);
                }
            });
            t_starttrans.setPriority(9);
            t_starttrans.start();

            t_perf_adjuster = new Thread(new Runnable() {
                @Override
                public void run() {
                    Base.temper_performance(act, 10, 60, 5);
                }
            });
            t_perf_adjuster.start();

            is_recording = true;

            act.h_main.postDelayed(new Runnable() {
                public void run() {
                    trans_update_runnable=this;
                    update_text();
                    if (is_recording) {
                        act.h_main.postDelayed(trans_update_runnable, 200);
                    }
                }
            }, 100);
            time_counter = new Timer();
            time_counter.scheduleAtFixedRate(new TimerTask() {
                int min = 0;
                int sec = 0;
                @Override
                public void run() {
                    sec++;
                    if (sec == 60) {
                        min++;
                        sec = 0;
                    }
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tv_counter.setText(String.valueOf(min) + ":" + String.valueOf(sec));
                        }
                    });
                }
            }, 0, 1000);
            tv_counter.setVisibility(View.VISIBLE);
            act.bottomNavigationView.setVisibility(View.GONE);
            fab_pause.show();
            act.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            stop_transcribe(view);
            if (act.settings.getBoolean("knows_conv_save", true)) {
                Toast.makeText(act, "The audio and transcript are saved by default.", Toast.LENGTH_SHORT).show();
                act.settings.edit().putBoolean("knows_conv_save", false).apply();
            }
        }
    }

    public void update_text() {
        String str = act.recEngine.get_text();
        String conststr = act.recEngine.get_const_text();
        int strlen = str.length();
        if (conststr.equals("")) {
            trans_text.replace(const_trans_size, const_trans_size + strlen, str);
        } else {
            const_trans_size = conststr.length();
            trans_text.replace(0, const_trans_size, conststr);
            trans_text.replace(const_trans_size, const_trans_size + strlen, str);
        }

        ed_transtext.setText(trans_text);
        ed_transtext.setSelection(trans_text.length());
    }

    public void pause_switch(View v) {
        if (!paused_stream) {
            paused_stream = true;
            fab_pause.setImageDrawable(act.getDrawable(R.drawable.mic_full_inv));
            act.recEngine.pause_switch_stream();
        } else {
            paused_stream = false;
            act.recEngine.pause_switch_stream();
            fab_pause.setImageDrawable(act.getDrawable(R.drawable.pause_c));
        }
    }

    public void handle_touch_event(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();

            int[] location_button = new int[2];
            fab_edit.getLocationOnScreen(location_button);
            Rect edit_rect = new Rect(location_button[0], location_button[1],
                    location_button[0] + ed_transtext.getWidth(),
                    location_button[1] + ed_transtext.getHeight());
            if (edit_rect.contains(x, y)) {  // inside edit button
                on_edit_click(v);
            } else if (v == ed_transtext) {
                int[] location = new int[2];
                ed_transtext.getLocationOnScreen(location);
                Rect transtext_rect = new Rect(location[0], location[1],
                        location[0] + ed_transtext.getWidth(),
                        location[1] + ed_transtext.getHeight());
                if (!transtext_rect.contains(x, y) && editing_transtext) {
                    on_edit_click(v);
                    Log.i("APP", "Closing keyboard text.");
                }
            } else if (v == ed_title) {
                int[] location = new int[2];
                ed_title.getLocationOnScreen(location);
                Rect titletext_rect = new Rect(location[0], location[1],
                        location[0] + ed_title.getWidth(),
                        location[1] + ed_title.getHeight());

                //Log.i("APP", "point " + event.getRawX() + " " + event.getRawY());
                //Log.i("APP", "l " + titletext_rect.left + " r " + titletext_rect.right + " t " + titletext_rect.top + " b " + titletext_rect.bottom);
                if (!titletext_rect.contains(x, y)) {
                    Log.i("APP", "Closing keyboard title.");
                    InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    ed_title.clearFocus();
                }
            }
        }
    }

    public boolean checkRecDone() {
        if (!recognition_done) {
            Toast.makeText(act.getApplicationContext(), "You have to transcribe something first!", Toast.LENGTH_SHORT).show();
            return false;
        } else return true;
    }

    public void on_copy_click(View view) {
        try {
            if (t_stoptrans != null) t_stoptrans.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!checkRecDone()) return;
        ClipboardManager clipboard = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        String text = ed_transtext.getText().toString();
        ClipData clip = ClipData.newPlainText("Transcript", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(act.getApplicationContext(), "Copied", Toast.LENGTH_SHORT).show();
        if (act.settings.getBoolean("knows_copy_paste", true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(act);
            builder.setTitle("Tip!")
                    .setMessage("Switch to another app of your choice, press and hold in a text-field for 1-2 seconds, release, and a \"paste\" option will appear.")
                    .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
            TextView tv = (TextView) alert.findViewById(android.R.id.message);
            tv.setTextSize(18);
            act.settings.edit().putBoolean("knows_copy_paste", false).apply();
        }
    }

    public void on_share_click(View view) {
        try {
            if (t_stoptrans != null) t_stoptrans.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!checkRecDone()) return;
        ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(fname_prefix);
        dialog.show(act.fragmentManager, "ShareDialog");
    }

    public void on_edit_click(View view) {
        ed_transtext.setFocusable(true);
        ed_transtext.setFocusableInTouchMode(true);
        Log.i("APP", "val " + editing_transtext);
        if (!editing_transtext) {
            Log.i("APP", "editing trans text");
            ed_title.setEnabled(false);
            fab_rec.hide();
            fab_share.hide();
            fab_copy.hide();
            fab_del.hide();
            fab_rec.hide();
            tv_transcribe_hint.setVisibility(View.INVISIBLE);
            editing_transtext = true;
            fab_edit.setImageResource(R.drawable.done);
            fab_edit.animate().translationY(-700);

            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ed_transtext, 0);

            text_textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(final Editable s) {
                    final String text = s.toString().replaceAll("(^\\s+|\\s+$)", "");

                    act.h_background.removeCallbacks(trans_edit_runnable);
                    trans_edit_runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FileWriter fw = new FileWriter(new File(filesdir + curr_afile.fname + Base.file_suffixes.get("text")), false);
                                fw.write(text);
                                fw.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    act.h_background.postDelayed(trans_edit_runnable, 500);
                }
            };
            ed_transtext.addTextChangedListener(text_textWatcher);

        } else {
            editing_transtext = false;
            Log.i("APP", "DONE EDIT");
            act.h_background.removeCallbacks(trans_edit_runnable);
            if (trans_edit_runnable != null) {
                act.h_background.post(trans_edit_runnable);
            }
            ed_transtext.removeTextChangedListener(text_textWatcher);
            fab_edit.animate().translationY(0.f);
            fab_edit.setImageResource(R.drawable.edit);
            fab_share.show();
            fab_copy.show();
            fab_del.show();
            fab_rec.show();
            tv_transcribe_hint.setVisibility(View.VISIBLE);
            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(ed_transtext.getWindowToken(), 0);
            ConstraintLayout.LayoutParams lay_params = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
            lay_params.bottomMargin = 8;
            img_view.invalidate();
            img_view.requestLayout();
            ed_transtext.clearFocus();
            ed_title.setEnabled(true);
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

    public void on_delete_click(View view) {
        manually_editing_title = true;
        ed_title.setText("", TextView.BufferType.EDITABLE);
        manually_editing_title = false;
        act.f_repo.delete(curr_afile);
        curr_afile = null;
        fab_edit.animate().translationX(256f);
        fab_copy.animate().translationX(256f);
        fab_share.animate().translationX(256f);
        fab_del.animate().translationX(-256f);
        ed_transtext.setText("");
    }

    public void resize_views(int height) {
        DisplayMetrics dm = new DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int dp = (int) (height / dm.density);
        if (editing_transtext) {
            ConstraintLayout.LayoutParams mainview_layout = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
            mainview_layout.bottomMargin = height - 100;
            img_view.invalidate();
            img_view.requestLayout();
        }
    }
}
