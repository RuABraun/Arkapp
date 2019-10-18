package typefree.typefree;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.bugsnag.android.Bugsnag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;
import static typefree.typefree.Base.sec_to_timestr;

public class FileInfo extends Fragment {

    private ImageButton mediaButton;
    private MediaPlayer mPlayer;
    private Runnable title_runnable, seekbar_runnable;
    private SeekBar mSeekBar;
    private AFile afile;
    private EditTextCursorListener ed_transtext;
    private EditText fileinfo_ed_title;
    private TextView tv_transtext, tv_fduration;
    private String text;
    private FloatingActionButton editButton;
    private ViewSwitcher viewSwitcher;
    private List<Integer> word_times_ms = new ArrayList<>();
    private List<String> original_words = new ArrayList<>();
    private List<Integer> word_start_c_idx = new ArrayList<>();  // start char
    private TextWatcher title_textWatcher;
    private SearchView.OnQueryTextListener search_textWatcher;
    private ImageView playView, fileViewHolder;
    private int playView_offset;
    private int fileholder_bottom_margin;
    private MainActivity act;
    private View fview;
    private ImageView cursortick, opts_menu;
    private Toolbar toolbar;
    private boolean editing_transtext = false;
    private int start_idx_colored = -1;
    private int end_idx_colored = -1;
    private SearchView trans_searchview;
    private int index_searchstart = 0;
    private int start_idx_highlighted = -1, end_idx_highlighted = -1;
    private boolean is_keyboard_open = false;
    private View.OnTouchListener transcript_touchListener;
    private int switcher_top_margin = -1;
    private Runnable reset_highlight_or_colored;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        afile = bundle.getParcelable("file_obj");
        playView_offset = 0;
        act = (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fileinfo, container, false);

        mediaButton = view.findViewById(R.id.mediaButton);
        editButton = view.findViewById(R.id.fileinfo_edit_button);
        viewSwitcher = view.findViewById(R.id.trans_view_switch);

        mSeekBar = view.findViewById(R.id.seekBar);

        ed_transtext = view.findViewById(R.id.ed_trans);
        tv_transtext = view.findViewById(R.id.tv_trans);
        fileinfo_ed_title = view.findViewById(R.id.fileinfo_ed_title);
        playView = view.findViewById(R.id.playView);
        fileViewHolder = view.findViewById(R.id.file_holder);
        opts_menu = view.findViewById(R.id.fileinfo_opts_menu);
        act.bottomNavigationView.setVisibility(View.INVISIBLE);
        trans_searchview = view.findViewById(R.id.fileinfo_searchview);

        toolbar = (Toolbar) view.findViewById(R.id.toolbar);

        view.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent event) {
                return true;
            }
        });

        String tag = "knows_can_press";
        if (!act.settings.getBoolean(tag, false)) {
            String msg = "Press on a word to start playing back the audio from when it was said.";
            TipDialog dialog = TipDialog.newInstance("Tip!", msg, tag);
            dialog.show(act.fragmentManager, "TipDialog");
        }
        this.fview = view;
        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onStart() {
        super.onStart();
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.fragmentManager.popBackStack();
            }
        });
        File f = new File(Base.filesdir + afile.fname + ".wav");
        mPlayer = MediaPlayer.create(act.getApplicationContext(), Uri.fromFile(f));
        mSeekBar.setMax(mPlayer.getDuration());
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mPlayer != null && fromUser) {
                    mPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        transcript_touchListener = new View.OnTouchListener() {
            int MAX_CLICK_DUR = 1500;
            int MAX_CLICK_DIST = 25;
            long click_start_time, click_last_time;
            int start_y, x, y;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        click_start_time = System.currentTimeMillis();
                        click_last_time = click_start_time;
                        x = (int) event.getX();
                        start_y = (int) event.getY();
                        y = start_y;
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        int new_y = (int) event.getY();
                        int dy = new_y - y;
                        int curr_scroll_y = tv_transtext.getScrollY();
                        if (curr_scroll_y - dy < 0) {
                            dy = curr_scroll_y;
                            new_y = y + dy;
                        }
                        int size_text_y = tv_transtext.getLineCount() * tv_transtext.getLineHeight();
                        int tv_height = tv_transtext.getHeight();

                        // for when text is smaller than textview
                        if (size_text_y < tv_height) {
                            dy = 0;
                        } else if (curr_scroll_y - dy > (size_text_y - tv_height)) {
                            // for when text is larger than textview
                            dy = curr_scroll_y - (size_text_y - tv_height);
                            new_y = y + dy;
                        }

                        tv_transtext.scrollBy(0, -dy);
                        y = new_y;
                        click_last_time = System.currentTimeMillis();
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        long cur_time = System.currentTimeMillis();
                        long dt = cur_time - click_start_time;
                        int new_y = (int) event.getY();
                        int dy = new_y - start_y;

                        if ((abs(dy) > MAX_CLICK_DIST) || (dt > MAX_CLICK_DUR)) {

                            float vel = ((float) dy) / ((float) dt);
                            int dist = (int) vel * 50;
                            int curr_scroll_y = tv_transtext.getScrollY();
                            Log.i("APP", " " + curr_scroll_y + " " + dist + " " + dy);
                            if (curr_scroll_y - dist < 0) {
                                dist = curr_scroll_y;
                            }
                            int size_text_y = tv_transtext.getLineCount() * tv_transtext.getLineHeight();
                            int tv_height = tv_transtext.getHeight();
                            Log.i("APP", "scrollinga " + dist);
                            if (size_text_y < tv_height) {
                                dist = 0;
                            } else if (curr_scroll_y - dist > (size_text_y - tv_height)) {
                                // for when text is larger than textview
                                dist = curr_scroll_y - (size_text_y - tv_height);
                            }
                            tv_transtext.scrollBy(0, -dist);
                            Log.i("APP", "scrolling " + dist);
                        } else {
                            Layout layout = tv_transtext.getLayout();
                            int yscroll = tv_transtext.getScrollY();
                            int lineidx = layout.getLineForVertical(y + yscroll);
                            // if the line idx is the same it means the click position is far below
                            // the text and we don't want to play
                            int lineidx_check = layout.getLineForVertical(y + yscroll - tv_transtext.getLineHeight()*2);
                            if (lineidx != lineidx_check || lineidx == 0) {
                                int offset = layout.getOffsetForHorizontal(lineidx, x);
                                int i = getIdxForCharOffset(offset);
                                if (i == -1 || i >= word_times_ms.size()) return false;
                                int time_ms = word_times_ms.get(i);
                                mPlayer.seekTo(time_ms);
                                playMediaPlayer();
                                reset_colored_text();
                                Spannable s = (Spannable) tv_transtext.getText();
                                String word = original_words.get(i);
                                int startidx = word_start_c_idx.get(i);
                                ed_transtext.setSelection(startidx);
                                int endidx = startidx + word.length();
                                s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(act, R.color.colorPrimary)),
                                        startidx, endidx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                start_idx_colored = startidx;
                                end_idx_colored = endidx;
                                act.h_main.removeCallbacks(reset_highlight_or_colored);
                                act.h_main.postDelayed(reset_highlight_or_colored, 5000);
                            }
                        }
                        return false;
                    }
                }
                return false;
            }
        };
        tv_transtext.setOnTouchListener(transcript_touchListener);

        if (text == null) {
            setFileFields();
        }

        opts_menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(act, v);
                popup.inflate(R.menu.menu_fileinternal);
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.IntShare:
                                pausePlaying();
                                ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(afile.fname);
                                dialog.show(act.fragmentManager, "ShareDialog");
                                return true;
                            case R.id.IntCopy:
                                ClipboardManager clipboard = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                                String simpletext = ed_transtext.getText().toString();
                                ClipData clip = ClipData.newPlainText("Transcript", simpletext);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(getActivity(), "Copied", Toast.LENGTH_SHORT).show();
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
                                return true;
                            case R.id.IntDelete:
                                pausePlaying();
                                AlertDialog.Builder builder = new AlertDialog.Builder(act);
                                builder.setTitle("Confirm")
                                        .setMessage("Are you sure?")
                                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int which) {
                                                delete(afile);
                                                act.fragmentManager.popBackStack();
                                            }
                                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                });
                                AlertDialog alert = builder.create();
                                alert.show();
                                TextView tv = (TextView) alert.findViewById(android.R.id.message);
                                tv.setTextSize(18);
                                return true;
                        }
                        return false;
                    }
                });
                popup.show();

            }
        });

    }

    private int getIdxForCharOffset(int offset) {
        int sz = word_start_c_idx.size();
        int i = 0;
        if (sz == 0) return -1;
        while(word_start_c_idx.get(i) < offset) {
            i++;
            if (i == sz) break;
        }
        i--;
        return i;
    }

    public void setFileFields() {
        Bugsnag.leaveBreadcrumb("Setting file fields.");
        TextView tv = getView().findViewById(R.id.file_date);
        tv.setText(afile.date);
        tv_fduration = getView().findViewById(R.id.file_duration);
        int file_len_s = afile.len_s;
        tv_fduration.setText(sec_to_timestr(file_len_s));

        String fpath = Base.filesdir + afile.fname + Base.file_suffixes.get("text");
        String normal_text;
        boolean text_found = true;
        try {
            FileInputStream fis = new FileInputStream(fpath);
            int size = fis.available();
            byte[] buffer = new byte[size];
            fis.read(buffer);
            fis.close();
            normal_text = new String(buffer);
            String text_no_whitespace = normal_text.replaceAll("\\\\s", "");
            if (text_no_whitespace.length() == 0) {
                text_found = false;
            }
        } catch(IOException ex) {
            text_found = false;
            normal_text = "Transcript not found! You might want to return to the \"Files\" screen to press the microphone image to transcribe it!";
        }
        text = normal_text;
        ed_transtext.setText(text);
        cursortick = getView().findViewById(R.id.cursortick);
        cursortick.setVisibility(View.INVISIBLE);
        ed_transtext.setCallback(new CursorCallback() {
            @Override
            public void onSelectionChanged(int startoffset) {
                int wordidx = getIdxForCharOffset(startoffset);
                if (wordidx == -1 || word_times_ms.size() == 0) return;
                int time_ms = word_times_ms.get(wordidx);
                float ratio = (float) time_ms / mPlayer.getDuration();
                Log.i("APP", "ratio " + ratio);
//                mSeekBar.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int seekbar_width = mSeekBar.getWidth() - mSeekBar.getPaddingLeft() - mSeekBar.getPaddingRight();  // -32 because of padding 16
                int xoffset = (int) (ratio * seekbar_width);
                Log.i("APP", "xoffset  " + xoffset );
                AnimatorSet animSetXY = new AnimatorSet();
                ObjectAnimator animx = ObjectAnimator.ofFloat(cursortick, "translationX", xoffset);
                ObjectAnimator animy = ObjectAnimator.ofFloat(cursortick, "translationY", playView_offset);
                animSetXY.playTogether(animx, animy);
                animSetXY.setInterpolator(new LinearInterpolator());
                animSetXY.setDuration(1);
                animSetXY.start();
            }
        });
        tv_transtext.setText(text, TextView.BufferType.SPANNABLE);

        if (text_found) {
            fpath = Base.filesdir + afile.fname + Base.file_suffixes.get("timed");
            String text_timed_str;
            try {
                FileInputStream fis = new FileInputStream(fpath);
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                text_timed_str = new String(buffer);
            } catch (IOException ex) {
                text_timed_str = "Timed text file not found!";
            }
            StringReader reader = new StringReader(text_timed_str);
            BufferedReader br = new BufferedReader(reader);
            String line = "";
            try {
                line = br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            int idx = -1;
            int num_char = 0;
            while (line != null) {
                String[] line_split = line.split(" ");
                String word = line_split[0];  // word and space
                original_words.add(word);
                final int word_time_ms = (int) (Float.parseFloat(line_split[1]) * 1000.0f);
                word_times_ms.add(word_time_ms);
                int saveidx = idx;
                idx = normal_text.indexOf(word, idx + 1);
                if (idx == -1) {
                    idx = saveidx;
                    try {
                        line = br.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                word_start_c_idx.add(idx);
                try {
                    line = br.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        fileinfo_ed_title.setText(afile.title);

        act.f_repo = new FileRepository(act.getApplication());
        Bugsnag.leaveBreadcrumb("Finished setting file fields");
    }

    public void playMediaPlayer() {
        mediaButton.setImageResource(R.drawable.pause);
        if (seekbar_runnable != null) {
            act.h_main.removeCallbacks(seekbar_runnable);
        }

        seekbar_runnable = new Runnable() {
            @Override
            public void run() {
                int cur_time_ms = mPlayer.getCurrentPosition();
                mSeekBar.setProgress(cur_time_ms);
                Layout layout = tv_transtext.getLayout();
                int h = tv_transtext.getMeasuredHeight();
                int yscroll = tv_transtext.getScrollY();
                int lineidx = layout.getLineForVertical(h + yscroll - 24) - 1;
                if (lineidx < 0) lineidx = 0;
                int offset = layout.getOffsetForHorizontal(lineidx, tv_transtext.getMeasuredWidth());
                int idx = getIdxForCharOffset(offset);
                if (word_times_ms.size() > 0) {
                    if (word_times_ms.get(idx) + 1000 < cur_time_ms && idx < word_times_ms.size() - 8) {  // otherwise scrolls at end of text
                        int line_height = tv_transtext.getLineHeight();
                        tv_transtext.scrollBy(0, line_height);
                    }
                }

                act.h_main.postDelayed(this, 100);
            }
        };

        act.h_main.post(seekbar_runnable);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mSeekBar.setProgress(0);
                mediaButton.setImageResource(R.drawable.play);
                act.h_main.removeCallbacks(seekbar_runnable);
            }
        });
        mPlayer.setVolume(1.0f, 1.0f);
        mPlayer.start();
    }

    public void on_media_click(View view) {

        if (!mPlayer.isPlaying()) {
            playMediaPlayer();
        } else {
            pausePlaying();
        }
    }


    public void pausePlaying() {
        mediaButton.setImageResource(R.drawable.play);
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                if (act.h_main != null) {
                    act.h_main.removeCallbacks(seekbar_runnable);
                }
            }
        }
    }

    public Rect create_RectFromView(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + view.getWidth(),
                location[1] + view.getHeight());
    }

    public void handle_touch_event(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();

            Rect edit_rect = create_RectFromView(editButton);
            Rect search_rect = create_RectFromView(trans_searchview);

            if (edit_rect.contains(x, y) && view != fileinfo_ed_title) {
                on_edit_click(view);
                return;
            }
            if (view == ed_transtext) {
                Rect rect = create_RectFromView(ed_transtext);
                if (!rect.contains(x, y) && is_keyboard_open) {
                    on_edit_click(view);
                }
                return;
            }

            if (view == fileinfo_ed_title) {
                Rect rect = create_RectFromView(fileinfo_ed_title);
                if (!rect.contains(x, y)) {
                    InputMethodManager imm = (InputMethodManager) act.getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    fileinfo_ed_title.clearFocus();
                }
                return;
            }

            if (search_rect.contains(x, y)) {
                if (!is_keyboard_open) {
                    adjust_for_keyboard_opening();
                }
            } else if (is_keyboard_open) {
                adjust_for_keyboard_closing();
            }
        }

    }

    public void on_edit_click(View view) {
        ed_transtext.setFocusable(true);
        ed_transtext.setFocusableInTouchMode(true);
        Log.i("APP", "in edit click");
        if (!editing_transtext) {
            editing_transtext = true;
            viewSwitcher.showNext();
            cursortick.setVisibility(View.VISIBLE);
            fileinfo_ed_title.setEnabled(false);

            ed_transtext.requestFocus();
            trans_searchview.setQuery("", false);

            editButton.setImageResource(R.drawable.done);

            trans_searchview.setVisibility(View.INVISIBLE);

            adjust_for_keyboard_opening();

            ConstraintLayout.LayoutParams trans_switcher_layout = (ConstraintLayout.LayoutParams) viewSwitcher.getLayoutParams();
            switcher_top_margin = trans_switcher_layout.topMargin;
            trans_switcher_layout.topMargin = trans_switcher_layout.topMargin - trans_searchview.getHeight();
            viewSwitcher.invalidate();
            viewSwitcher.requestLayout();

        } else {
            // TODO: crashes when called on text that was edited!!
            editing_transtext = false;
            cursortick.setVisibility(View.INVISIBLE);
            fileinfo_ed_title.setEnabled(true);

            trans_searchview.setVisibility(View.VISIBLE);

            ConstraintLayout.LayoutParams trans_switcher_layout = (ConstraintLayout.LayoutParams) viewSwitcher.getLayoutParams();
            trans_switcher_layout.topMargin = switcher_top_margin;
            viewSwitcher.invalidate();
            viewSwitcher.requestLayout();

            adjust_for_keyboard_closing();

            String new_text = ed_transtext.getText().toString().replaceAll("(^\\s+|\\s+$)", "");
            FileWriter fw = null;
            try {
                fw = new FileWriter(new File(Base.filesdir + afile.fname + Base.file_suffixes.get("text")), false);
                fw.write(new_text);
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            tv_transtext.setText(new_text);
            text = new_text;

            editButton.setImageResource(R.drawable.edit);
            viewSwitcher.showNext();
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

    public void adjust_for_keyboard_opening() {
        if (is_keyboard_open) return;
        is_keyboard_open = true;
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(ed_transtext, InputMethodManager.SHOW_IMPLICIT);
        tv_transtext.setOnTouchListener(null);
        int playview_top = playView.getTop() - 16;
        playView_offset = -playview_top;
        playView.animate().translationY(playView_offset);
        mediaButton.animate().translationY(playView_offset);
        tv_fduration.animate().translationY(playView_offset);
        cursortick.animate().translationY(playView_offset);
        mSeekBar.animate().translationY(playView_offset);
        fileinfo_ed_title.setVisibility(View.INVISIBLE);
    }

    public void adjust_for_keyboard_closing() {
        if (!is_keyboard_open) return;
        is_keyboard_open = false;
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(act.getWindow().getCurrentFocus().getWindowToken(), 0);
        tv_transtext.setOnTouchListener(transcript_touchListener);
        playView_offset = 0;
        playView.animate().translationY(0);
        mediaButton.animate().translationY(0);
        tv_fduration.animate().translationY(0);
        mSeekBar.animate().translationY(0);
        cursortick.animate().translationY(0);
        fileViewHolder.animate().translationY(0);
        cursortick.animate().translationX(0);
        fileinfo_ed_title.setVisibility(View.VISIBLE);

        ConstraintLayout.LayoutParams fv_lay_params = (ConstraintLayout.LayoutParams) fileViewHolder.getLayoutParams();
        Log.i("APP", "length " + fileholder_bottom_margin);
        fv_lay_params.bottomMargin = fileholder_bottom_margin;
        fileViewHolder.invalidate();
        fileViewHolder.requestLayout();
    }

    public void delete(AFile afile) {
        act.f_repo.delete(afile);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(null);
        }
        if (tv_transtext != null) {
            tv_transtext.setOnTouchListener(null);
        }
        if (mPlayer != null) {
            pausePlaying();
            mPlayer.stop();
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        act.bottomNavigationView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();

        title_textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(final Editable s) {
                String cname = s.toString().replaceAll("(^\\s+|\\s+$)", "");
                if (cname.equals("")) {
                    cname = getString(R.string.default_convname);
                }
                final String curname = cname;
                final String fname = MainActivity.getFileName(cname, act.f_repo);
                act.h_background.removeCallbacks(title_runnable);

                title_runnable = new Runnable() {
                    @Override
                    public void run() {
                        act.f_repo.rename(afile, curname, fname);
                        afile.title = curname;
                        afile.fname = fname;
                    }
                };
                act.h_background.postDelayed(title_runnable, 500);

            }
        };
        fileinfo_ed_title.addTextChangedListener(title_textWatcher);

        reset_highlight_or_colored = new Runnable() {
            @Override
            public void run() {
                reset_colored_text();
                reset_highlighted_text();
                act.h_main.postDelayed(this, 5000);
            }
        };

        search_textWatcher = new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String search_string) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String search_string) {
                int str_len = search_string.length();
                if (str_len < 2) {
                    reset_highlighted_text();
                    return false;
                }
                act.h_main.removeCallbacks(reset_highlight_or_colored);
                act.h_main.postDelayed(reset_highlight_or_colored, 5000);
                int index = text.indexOf(search_string, index_searchstart);
                if (index == -1) return false;
                Spannable str = (Spannable) tv_transtext.getText();
                start_idx_highlighted = index;
                end_idx_highlighted = index + str_len;
                str.setSpan(new BackgroundColorSpan(ContextCompat.getColor(act, R.color.colorAccent)),
                        start_idx_highlighted, end_idx_highlighted, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ed_transtext.setSelection(index);
                Layout layout = tv_transtext.getLayout();
                int line_idx = layout.getLineForOffset(index);
                int offset = line_idx * tv_transtext.getLineHeight() - tv_transtext.getScrollY();
                tv_transtext.scrollBy(0, offset);
                int i = getIdxForCharOffset(index);
                if (i == -1 || i >= word_times_ms.size()) return false;
                int time_ms = word_times_ms.get(i);
                mPlayer.seekTo(time_ms);
                mSeekBar.setProgress(time_ms);
                return false;
            }
        };

        trans_searchview.setOnQueryTextListener(search_textWatcher);
    }

    @Override
    public void onPause() {
        pausePlaying();
        if (title_runnable != null) {
            act.h_background.removeCallbacks(title_runnable);
            title_runnable.run();
        }
        if (reset_highlight_or_colored != null) {
            act.h_main.removeCallbacks(reset_highlight_or_colored);
        }
        fileinfo_ed_title.removeTextChangedListener(title_textWatcher);
        trans_searchview.setOnQueryTextListener(null);
        super.onPause();
    }

    public void resize_views(int height) {
        // is called twice per action for some reason
        if (is_keyboard_open) {
            ConstraintLayout.LayoutParams mainview_layout = (ConstraintLayout.LayoutParams) fileViewHolder.getLayoutParams();
            if (mainview_layout.bottomMargin < 100) {
                fileholder_bottom_margin = mainview_layout.bottomMargin;
            }
            //Log.i("APP", "setlength " + fileholder_bottom_margin + " height " + height);
            mainview_layout.bottomMargin = height - 125;
            fileViewHolder.invalidate();
            fileViewHolder.requestLayout();
        }
    }

    public void reset_colored_text() {
        if (start_idx_colored != -1) {
            Spannable s = (Spannable) tv_transtext.getText();
            s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(act, R.color.colorText)),
                    start_idx_colored, end_idx_colored, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

    }

    public void reset_highlighted_text() {
        if (start_idx_highlighted != -1) {
            Spannable str = (Spannable) tv_transtext.getText();
            str.setSpan(new BackgroundColorSpan(ContextCompat.getColor(act, R.color.colorDefaultWhite)),
                    start_idx_highlighted, end_idx_highlighted, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start_idx_highlighted = -1;
            end_idx_highlighted = -1;
        }
    }
}

