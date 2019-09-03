package typefree.typefree;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static typefree.typefree.Base.convertPixelsToDp;
import static typefree.typefree.Base.filesdir;


public class MainFragment extends Fragment {


    public boolean is_recording = false;
    Runnable title_runnable;
    Runnable runnable, trans_update_runnable;
    Runnable trans_done_runnable;
    Runnable trans_edit_runnable;
    private EditText ed_transtext;
    private FloatingActionButton fab_rec, fab_edit, fab_copy, fab_share, fab_del;
    private EditText ed_title;
    private String fname_prefix = "";
    private String curr_cname = "";
    private boolean recognition_done = false;  // so we know if we have to modify a text file or rename files of conversation
    private AFile curr_afile;
    private int const_trans_size = 0;
    private ProgressBar spinner;
    Thread t_stoptrans, t_starttrans;
    private boolean edited_title = false;  // we dont want to call afterTextChanged because we set the title (as happens at the start of recognition)
    private boolean editing_title = false;
    private boolean is_editing = false, just_closed = false;
    private TextView tv_counter;
    private ImageView img_view;
    private TextWatcher title_textWatcher, text_textWatcher;
    private MainActivity act;
    private View fview;
    private StringBuilder trans_text;
    ObjectAnimator pulse;
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
        tv_counter = view.findViewById(R.id.tv_counter);
        tv_counter.setVisibility(View.INVISIBLE);

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
        fab_copy.setTranslationX(256f);
        fab_share.setTranslationX(256f);
        fab_del.setTranslationX(-256f);

//        int counter_bottom = tv_counter.getBottom();
//        int view_bottom = img_view.getBottom();
//
//        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) ed_transtext.getLayoutParams();
//        params.matchConstraintMaxHeight =

        this.fview = view;
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        act.h_main.postDelayed(new Runnable() {
            public void run() {
                runnable=this;
                act.h_main.postDelayed(runnable, 100);
                if (RecEngine.isready) {
                    fab_rec.show();
                    spinner.setVisibility(View.INVISIBLE);
                    act.h_main.removeCallbacks(runnable);
                    pulse = ObjectAnimator.ofPropertyValuesHolder(fab_rec,
                            PropertyValuesHolder.ofFloat("scaleX", 1.15f),
                            PropertyValuesHolder.ofFloat("scaleY", 1.15f));
                    pulse.setDuration(300);
                    pulse.setStartDelay(1000);
                    pulse.setRepeatCount(1);
                    pulse.setRepeatMode(ObjectAnimator.REVERSE);
                    pulse.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            pulse.setStartDelay(2000);
                            pulse.start();
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    pulse.start();
                }
            }
        }, 100);

        DisplayMetrics dm = new DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenHeight = dm.heightPixels;

        Log.i("APP", "height " + screenHeight + " " + dm.density);

        if (act.settings.getBoolean("knows_mic_location", true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(act);
            builder.setTitle("Tip!")
                    .setMessage("Point the microphone, typically at the bottom of your phone, towards the speaker (the closer the better).")
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
                    if (recognition_done && !edited_title) {
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
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(ed_transtext.getWindowToken(), 0);
        super.onPause();
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
    }


    public void stop_transcribe() {
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
        String fname = act.getFileName(curr_cname, act.f_repo);
        fname_prefix = fname;

        MainActivity.renameConv("tmpfile", fname);

        int duration_s = (int) (3 * num_out_frames) / 100;
        AFile afile = new AFile(title, fname, duration_s, date);
        long id = act.f_repo.insert(afile);
        curr_afile = act.f_repo.getById(id);  // has correct ID
        Log.i("APP", "FILE ID " + curr_afile.getId() + " title: " + title + " fname: " + fname);
        recognition_done = true;
    }

    public void record_switch(View view) {
        if (!is_recording ) {
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
                edited_title = true;
                ed_title.setText(curr_cname);
                edited_title = false;
            }

            recognition_done = false;
            fab_edit.animate().translationX(256f);
            fab_copy.animate().translationX(256f);
            fab_share.animate().translationX(256f);
            fab_del.animate().translationX(-256f);
            float offset = (float) fab_edit.getLeft() - fab_rec.getLeft() - 4;
            pulse.cancel();
            fab_rec.animate().translationX(offset);
            fab_rec.setImageResource(R.drawable.stop);
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
            act.h_background.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Base.temper_performance(act, 10, 60, 5);
                }
            }, 500);

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

        } else {
            time_counter.cancel();
            spinner.setVisibility(View.VISIBLE);
            is_recording = false;
            RecEngine.isrunning = false;
            if (act.pm.isSustainedPerformanceModeSupported()) {
                Log.i("APP", "Turning sustainedperf off for good");
                act.getWindow().setSustainedPerformanceMode(false);
            }

            act.h_main.postDelayed(new Runnable() {
                public void run() {
                    trans_done_runnable=this;
                    if (recognition_done) {
                        fab_rec.animate().translationX(0f);
                        fab_rec.setImageResource(R.drawable.mic_full_inv);
                        pulse.start();
                        spinner.setVisibility(View.INVISIBLE);
                        act.h_main.removeCallbacks(trans_done_runnable);
                        fab_edit.animate().translationX(0f);
                        fab_copy.animate().translationX(0f);
                        fab_share.animate().translationX(0f);
                        fab_del.animate().translationX(0f);
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
                    stop_transcribe();
                    Log.i("APP", "Finished recording.");
                }
            });
            t_stoptrans.setPriority(8);
            t_stoptrans.start();

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

    public void handle_touch_event(View v, MotionEvent event) {
        if (v == ed_transtext) {
            int x = convertPixelsToDp(event.getRawX(), act);
            int y = convertPixelsToDp(event.getRawY(), act);
            if (event.getAction() == MotionEvent.ACTION_UP && (x < v.getLeft() || x >= v.getRight() || y < v.getTop() || y > v.getBottom()) && is_editing) {
                InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
                //Log.i("APP", "Closing keyboard text.");
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                fab_edit.animate().translationY(0.f);
                ed_transtext.clearFocus();
                ed_transtext.setFocusableInTouchMode(false);
                is_editing = false;
                just_closed = true;
            }
        } else {
            int x = convertPixelsToDp(event.getRawX(), act);
            int y = convertPixelsToDp(event.getRawY(), act);
            //Log.i("APP", x + " " + y + " " + ed_title.getLeft() + "-"+ed_title.getRight() + " " + ed_title.getTop()+"-"+ed_title.getBottom());
            if (x < ed_title.getLeft() || x >= ed_title.getRight() || y < ed_title.getTop() || y > ed_title.getBottom()) {
                if (v == ed_title && event.getAction() == MotionEvent.ACTION_UP) {
                    //Log.i("APP", "Closing keyboard title.");
                    InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    fab_share.show();
                    fab_copy.show();
                    fab_del.show();
                    fab_rec.show();
                    fab_edit.show();
                    editing_title = false;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                //Log.i("APP", "Editing title.");
                editing_title = true;
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

        if (!is_editing && !just_closed) {
            is_editing = true;
            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ed_transtext, InputMethodManager.SHOW_IMPLICIT);

            ed_transtext.invalidate();
            ed_transtext.requestLayout();

            fab_edit.setImageResource(R.drawable.done);
            fab_rec.hide();
            Log.i("APP", "IN TRANSEDIT EDIT PRESS " + act.keyboard_height);
            fab_edit.animate().translationY(-act.keyboard_height);
            fab_share.hide();
            fab_copy.hide();
            fab_del.hide();
            fab_rec.hide();
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
            just_closed = false;
            Log.i("APP", "DONE EDIT");
            act.h_background.removeCallbacks(trans_edit_runnable);
            if (trans_edit_runnable != null) {
                act.h_background.post(trans_edit_runnable);
            }
            ed_transtext.removeTextChangedListener(text_textWatcher);
            fab_edit.setImageResource(R.drawable.edit);
            fab_share.show();
            fab_copy.show();
            fab_del.show();
            fab_rec.show();
            is_editing = false;
            ConstraintLayout.LayoutParams lay_params = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
            lay_params.bottomMargin = 8;
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(ed_transtext.getWindowToken(), 0);
            img_view.invalidate();
            img_view.requestLayout();
            // everything already done in dispatchTouchEvent
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

    public void on_delete_click(View view) {
        edited_title = true;
        ed_title.setText("", TextView.BufferType.EDITABLE);
        act.f_repo.delete(curr_afile);
        curr_afile = null;
        fab_edit.animate().translationX(256f);
        fab_copy.animate().translationX(256f);
        fab_share.animate().translationX(256f);
        fab_del.animate().translationX(-256f);
        ed_transtext.setText("");
        edited_title = false;
    }

    public void resize_views(int height) {
        //Log.i("APP", "resizing");
        DisplayMetrics dm = new DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int dp = (int) (height / dm.density);
        fab_edit.animate().translationY(-dp);
        if (editing_title) {
            fab_share.hide();
            fab_copy.hide();
            fab_del.hide();
            fab_rec.hide();
            fab_edit.hide();
        }
        ConstraintLayout.LayoutParams mainview_layout = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
        mainview_layout.bottomMargin = height - 100;
        img_view.invalidate();
        img_view.requestLayout();
    }

}
