package typefree.typefree;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

import static typefree.typefree.Base.filesdir;


public class MainFragment extends Fragment {


    public boolean is_recording = false;
    Runnable title_runnable;
    Runnable runnable;
    Runnable trans_done_runnable;
    Runnable trans_edit_runnable;
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
    Thread t_stoptrans, t_starttrans;
    private boolean edited_title = false;  // we dont want to call afterTextChanged because we set the title (as happens at the start of recognition)
    private float ed_trans_to_edit_button_distance;
    private boolean is_editing = false, just_closed = false;
    private ProgressBar pb_init;
    private TextView tv_init;
    private ImageView img_view;
    private TextWatcher title_textWatcher, text_textWatcher;
    private MainActivity act;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private boolean showingKeyboard = false;
    private boolean hidingKeyboard = false;
    private View fview;

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
        pb_init = view.findViewById(R.id.progressBar_init_setup);
        tv_init = view.findViewById(R.id.textview_init_setup);

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

        fab_rec.setVisibility(View.INVISIBLE);
        spinner.setVisibility(View.VISIBLE);

        fab_edit = view.findViewById(R.id.button_edit);
        fab_edit.setTranslationX(128f);

        fab_copy = view.findViewById(R.id.button_copy);
        fab_share = view.findViewById(R.id.button_share);
        fab_copy.setTranslationX(128f);
        fab_share.setTranslationX(128f);
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
                    fab_rec.setVisibility(View.VISIBLE);
                    spinner.setVisibility(View.INVISIBLE);
                    act.h_main.removeCallbacks(runnable);
                }
            }
        }, 100);

        DisplayMetrics dm = new DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenHeight = dm.heightPixels;

        Log.i("APP", "height " + screenHeight + " " + dm.density);
        ed_trans_to_edit_button_distance = (screenHeight / dm.density) - 2 * ed_title.getMeasuredHeight();
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
        layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {

                Rect r = new Rect();
                fview.getWindowVisibleDisplayFrame(r);
                int screenheight = fview.getRootView().getHeight();
                int height_diff = screenheight - (r.bottom - r.top);
                if (height_diff > 120 && !hidingKeyboard) {
                    if (!showingKeyboard) {
                        ConstraintLayout.LayoutParams mainview_layout = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
                        showingKeyboard = true;
                        mainview_layout.bottomMargin = height_diff - 56  * 2;
                        img_view.invalidate();
                        img_view.requestLayout();
                    }
                } else {
                    hidingKeyboard = false;
                }

            }
        };
        fview.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
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
        if (trans_edit_runnable != null) {
            act.h_background.removeCallbacks(trans_edit_runnable);
            trans_edit_runnable.run();
        }
        ed_title.removeTextChangedListener(title_textWatcher);
        fview.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    }


    public void stop_transcribe() {
        long num_out_frames = act.recEngine.stop_trans_stream();
        if (curr_cname.equals("")) {
            curr_cname = getString(R.string.default_convname);
        }

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
        act.h_main.removeCallbacks(runnable);
        act.h_main.post(new Runnable() {
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
                const_transcript = "";
            }

            if (trans_edit_runnable != null) {
                act.h_background.removeCallbacks(trans_edit_runnable);
                act.h_background.post(trans_edit_runnable);
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
                    act.recEngine.transcribe_stream(fpath);
                }
            });
            t_starttrans.setPriority(7);
            t_starttrans.start();

            is_recording = true;

            act.h_main.postDelayed(new Runnable() {
                public void run() {
                    runnable=this;
                    update_text();
                    act.h_main.postDelayed(runnable, 25);
                }
            }, 25);

        } else {
            spinner.setVisibility(View.VISIBLE);
            is_recording = false;

            act.h_main.postDelayed(new Runnable() {
                public void run() {
                    trans_done_runnable=this;
                    act.h_main.postDelayed(trans_done_runnable, 100);
                    if (recognition_done) {
                        fab_rec.animate().translationX(0f);
                        fab_rec.setImageResource(R.drawable.mic_full_inv);
                        spinner.setVisibility(View.INVISIBLE);
                        act.h_main.removeCallbacks(trans_done_runnable);
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

    public void update_text() {
        String str = act.recEngine.get_text();
        String conststr = act.recEngine.get_const_text();
        String all = conststr + str;
        ed_transtext.setText(all, TextView.BufferType.EDITABLE);
        ed_transtext.setSelection(all.length());
    }

    public void handle_touch_event(View v, MotionEvent event) {
        if (v == ed_transtext) {
            View w = act.getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];
            int r = fab_edit.getLeft();
            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom()) && is_editing) {
                InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view = act.getCurrentFocus();
                if (view == null) {
                    view = new View(act);
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
            View w = act.getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];
            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom())) {
                InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view = act.getCurrentFocus();
                if (view == null) {
                    view = new View(act);
                }
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
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
            fab_rec.setVisibility(View.INVISIBLE);
            Log.i("APP", "IN TRANSEDIT EDIT PRESS " + ed_trans_to_edit_button_distance);
            fab_edit.animate().translationY(-(int)ed_trans_to_edit_button_distance*0.5f);
            fab_share.setVisibility(View.INVISIBLE);
            fab_copy.setVisibility(View.INVISIBLE);

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
            hidingKeyboard = true;
            showingKeyboard = false;
            Log.i("APP", "DONE EDIT");
            act.h_background.removeCallbacks(trans_edit_runnable);
            if (trans_edit_runnable != null) {
                act.h_background.post(trans_edit_runnable);
            }
            ed_transtext.removeTextChangedListener(text_textWatcher);
            fab_edit.setImageResource(R.drawable.edit);
            fab_rec.setVisibility(View.VISIBLE);
            is_editing = false;
            ConstraintLayout.LayoutParams lay_params = (ConstraintLayout.LayoutParams) img_view.getLayoutParams();
            lay_params.bottomMargin = 0;
            img_view.invalidate();
            img_view.requestLayout();
            // everything already done in dispatchTouchEvent
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

}
