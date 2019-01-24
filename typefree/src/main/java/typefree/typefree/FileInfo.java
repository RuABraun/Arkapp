package typefree.typefree;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.design.widget.FloatingActionButton;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

public class FileInfo extends Fragment {

    private ImageButton mediaButton;
    private MediaPlayer mPlayer;
    private Runnable title_runnable, seekbar_runnable, ed_trans_runnable;
    private SeekBar mSeekBar;
    private AFile afile;
    private EditText ed_transtext;
    private EditText fileinfo_ed_title;
    private TextView tv_transtext, tv_fduration;
    private String text;
    private FloatingActionButton editButton;
    private ViewSwitcher viewSwitcher;
    private List<Integer> word_times_ms = new ArrayList<>();
    private List<String> original_words = new ArrayList<>();
    private List<Integer> word_start_c_idx = new ArrayList<>();  // start char
    private boolean just_closed = false;
    private TextWatcher title_textWatcher, text_textWatcher;
    private ImageView playView;
    private int playView_offset;
    private MainActivity act;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        afile = savedInstanceState.getParcelable("file_obj");
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
        act.bottomNavigationView.setVisibility(View.INVISIBLE);
        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onStart() {
        super.onStart();

        mPlayer = MediaPlayer.create(act.getApplicationContext(), Uri.parse(Base.filesdir + afile.fname + ".wav"));
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

        tv_transtext.setOnTouchListener(new View.OnTouchListener() {
            int MAX_CLICK_DUR = 100;
            int MAX_CLICK_DIST = 10;
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
                        tv_transtext.scrollBy(0, -dy);
                        click_last_time = System.currentTimeMillis();
                        y = new_y;
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        long cur_time = System.currentTimeMillis();
                        long dt = cur_time - click_start_time;
                        int new_y = (int) event.getY();
                        int dy = new_y - start_y;
//                        Log.i("APP", "UP + " + dy + " " + dt);
                        if ((abs(dy) > MAX_CLICK_DIST) && (dt > MAX_CLICK_DUR)) {
                            long ddt = cur_time - click_last_time;
                            int ddy = new_y - y;
                            float vel = ((float) ddy) / ((float) ddt);
                            int dist = (int) vel * 10;
                        } else {
                            Log.i("APP", "PLAY");
                            Layout layout = tv_transtext.getLayout();
                            int yscroll = tv_transtext.getScrollY();
                            int lineidx = layout.getLineForVertical(y + yscroll);
                            int offset = layout.getOffsetForHorizontal(lineidx, x);
                            int i = getIdxForCharOffset(offset);
                            int time_ms = word_times_ms.get(i);
                            mPlayer.seekTo(time_ms);
                            playMediaPlayer();
                            Spannable s = (Spannable) tv_transtext.getText();
                            String word = original_words.get(i);
                            int startidx = word_start_c_idx.get(i);
                            int endidx = startidx + word.length();
                            s.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorPrimary)),
                                    startidx, endidx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        return false;
                    }
                }
                return false;
            }
        });

        if (text == null) {
            setFileFields();
        }

    }

    private int getIdxForCharOffset(int offset) {
        int sz = word_start_c_idx.size();
        int i = 0;
        while(word_start_c_idx.get(i) < offset) {
            i++;
            if (i == sz) break;
        }
        i--;
        return i;
    }

    public void setFileFields() {
        TextView tv = getView().findViewById(R.id.file_date);
        tv.setText(afile.date);
        tv_fduration = getView().findViewById(R.id.file_duration);
        int file_len_s = afile.len_s;
        tv_fduration.setText(String.valueOf(file_len_s));

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
        } catch(IOException ex) {
            text_found = false;
            normal_text = "Text file not found!";
        }
        text = normal_text;
        ed_transtext.setText(text);
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
                if (word_times_ms.get(idx) + 1000 < cur_time_ms && idx < word_times_ms.size() - 8) {  // otherwise scrolls at end of text
                    int line_height = tv_transtext.getLineHeight();
                    tv_transtext.scrollBy(0, line_height);
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

    public void handle_touch_event(View view, MotionEvent event) {
        if (view instanceof EditText) {
            int scrcoords[] = new int[2];
            view.getLocationOnScreen(scrcoords);
            Log.i("APP", ed_transtext.getLeft()+ " "+ed_transtext.getRight() + " " +ed_transtext.getTop()+" " +ed_transtext.getBottom());
            Log.i("APP", view.getLeft() + " " + view.getTop() + " " + view.getRight());
            Log.i("APP", scrcoords[0] + " " + scrcoords[1]);
            int x = (int) event.getRawX();
            int y = (int) event.getRawY();
            int view_left = view.getLeft() + scrcoords[0], view_right = view.getRight() + scrcoords[0],
                    view_top = view.getTop() + scrcoords[1], view_bottom = view.getBottom() + scrcoords[1];
            Log.i("APP", event.getRawX() + " " + event.getRawY());
            Log.i("APP", x + " " + y);

            //Log.d("Activity", "Touch event "+event.getRawX()+","+event.getRawY()+" "+x+","+y+" rect "+w.getLeft()+","+w.getTop()+","+w.getRight()+","+w.getBottom()+" coords "+scrcoords[0]+","+scrcoords[1]);
            Log.d("APP", "top " + playView.getTop() + " " +playView.getBottom() + " "+playView_offset + " " + y);
            if (event.getAction() == MotionEvent.ACTION_UP && (x < view_left || x >= view_right || y < view_top || y > view_bottom) &&
                    (x < playView.getLeft() || x > playView.getRight() || y > (playView.getBottom()+playView_offset) || y < (playView.getTop()+playView_offset))) {
                InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(act.getWindow().getCurrentFocus().getWindowToken(), 0);

                ed_transtext.clearFocus();
                ed_transtext.setFocusableInTouchMode(false);
                just_closed = true;
            }
        }
    }

    public void on_edit_click(View view) {
        ed_transtext.setFocusable(true);
        ed_transtext.setFocusableInTouchMode(true);

        if (!ed_transtext.hasFocus() && !just_closed) {
            viewSwitcher.showNext();

            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ed_transtext, InputMethodManager.SHOW_IMPLICIT);
            editButton.setImageResource(R.drawable.done);

            text_textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    final String new_text = s.toString().replaceAll("(^\\s+|\\s+$)", "");
                    act.h_background.removeCallbacks(ed_trans_runnable);
                    ed_trans_runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FileWriter fw = new FileWriter(new File(Base.filesdir + afile.fname + Base.file_suffixes.get("text")), false);
                                fw.write(new_text);
                                fw.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    act.h_background.postDelayed(ed_trans_runnable, 500);
                }
            };
            ed_transtext.addTextChangedListener(text_textWatcher);

            int playview_top = playView.getTop() - 4;
            playView_offset = -playview_top;
            playView.animate().translationY(-playview_top);
            mediaButton.animate().translationY(-playview_top);
            tv_fduration.animate().translationY(-playview_top);
            mSeekBar.animate().translationY(-playview_top);

        } else {
            // TODO: crashes when called on text that was edited!!
            just_closed = false;
            playView_offset = 0;
            playView.animate().translationY(0);
            mediaButton.animate().translationY(0);
            tv_fduration.animate().translationY(0);
            mSeekBar.animate().translationY(0);

            if (ed_trans_runnable != null) {
                act.h_background.removeCallbacks(ed_trans_runnable);
                ed_trans_runnable.run();
            }
            ed_transtext.removeTextChangedListener(text_textWatcher);

            final String new_text = ed_transtext.getText().toString();
            tv_transtext.setText(new_text);

            editButton.setImageResource(R.drawable.edit);
            viewSwitcher.showNext();
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

    public void on_copy_click(View view) {

        ClipboardManager clipboard = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        String simpletext = ed_transtext.getText().toString();
        ClipData clip = ClipData.newPlainText("Transcript", simpletext);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(act.getApplicationContext(), "Copied", Toast.LENGTH_SHORT).show();
    }

    public void on_share_click(View view) {
        ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(afile.fname);
        dialog.show(getFragmentManager(), "ShareDialog");
    }

    public void on_delete_click(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setTitle("Confirm")
                .setMessage("Are you sure?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        delete(afile);
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
                Log.i("APP", "In text change.");
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
                        Log.i("APP", "fname " + fname + " cname " + curname);
                        act.f_repo.rename(afile, curname, fname);
                        afile.title = curname;
                        afile.fname = fname;
                    }
                };
                act.h_background.postDelayed(title_runnable, 500);

            }
        };
        fileinfo_ed_title.addTextChangedListener(title_textWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();
        pausePlaying();
        if (title_runnable != null) {
            act.h_background.removeCallbacks(title_runnable);
            title_runnable.run();
        }
        if (ed_trans_runnable != null) {
            act.h_background.removeCallbacks(ed_trans_runnable);
            ed_trans_runnable.run();
        }
        fileinfo_ed_title.removeTextChangedListener(title_textWatcher);
    }

//    @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        Intent intent = new Intent(getApplicationContext(), Manage.class);
//        startActivity(intent);
//        finish();
//    }
}

abstract class ClickableSpanPlain extends ClickableSpan {
    public void updateDrawState(TextPaint ds) {
        ds.setUnderlineText(false);
    }
}