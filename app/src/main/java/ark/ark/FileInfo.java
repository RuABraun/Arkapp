package ark.ark;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.arch.lifecycle.ViewModelProviders;
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
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.abs;

public class FileInfo extends Base {

    private ImageButton mediaButton;
    private MediaPlayer mPlayer;
    private HandlerThread handlerThread;
    private Handler h_background;
    private Handler h_main = new Handler(Looper.getMainLooper());
    private Runnable title_runnable, seekbar_runnable, ed_trans_runnable, scroll_runnable;
    private SeekBar mSeekBar;
    private AFile afile;
    private EditText ed_transtext;
    private EditText fileinfo_ed_title;
    private TextView tv_transtext;
    private String text;
    private FileRepository f_repo;
    private FloatingActionButton editButton;
    private ViewSwitcher viewSwitcher;
    private List<Integer> word_times_ms = new ArrayList<>();
    private List<String> original_words = new ArrayList<>();
    private List<Integer> word_start_c_idx = new ArrayList<>();  // start char
    private boolean just_closed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fileinfo);

        afile = getIntent().getParcelableExtra("file_obj");

        mediaButton = findViewById(R.id.mediaButton);
        editButton = findViewById(R.id.fileinfo_edit_button);
        viewSwitcher = findViewById(R.id.trans_view_switch);

        mSeekBar = findViewById(R.id.seekBar);

        ed_transtext = findViewById(R.id.ed_trans);
        tv_transtext = findViewById(R.id.tv_trans);
        fileinfo_ed_title = findViewById(R.id.fileinfo_ed_title);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onStart() {
        super.onStart();
        File file = new File(filesdir + afile.fname + ".wav");
        mPlayer = MediaPlayer.create(getApplicationContext(), Uri.parse(filesdir + afile.fname + ".wav"));
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
//                            int cur_y_scroll = tv_transtext.getScrollY();
//                            int new_y_scroll = cur_y_scroll - dist;
//                            tv_transtext.measure(0, 0);
//                            int h = tv_transtext.getMeasuredHeight();
//                            if (new_y_scroll < 0) new_y_scroll = 0;
//                            if (new_y_scroll > h) new_y_scroll = h;
//                            tv_transtext.scrollTo(0, new_y_scroll);

//                            if (cur_y_scroll < 0) tv_transtext.scrollTo(0, 0);
//                            else if (cur_y_scroll > h) tv_transtext.scrollTo(0, h);
                        } else {
                            Log.i("APP", "PLAY");
                            Layout layout = tv_transtext.getLayout();
                            int lineidx = layout.getLineForVertical(y);
                            int offset = layout.getOffsetForHorizontal(lineidx, x);
                            int sz = word_start_c_idx.size();
                            int i = 0;
                            while(word_start_c_idx.get(i) < offset) {
                                i++;
                                if (i == sz) break;
                            }
                            i--;
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

    public void setFileFields() {
        TextView tv = findViewById(R.id.file_date);
        tv.setText(afile.date);
        tv = findViewById(R.id.file_duration);
        int file_len_s = afile.len_s;
        tv.setText(String.valueOf(file_len_s));

        String fpath = filesdir + afile.fname + file_suffixes.get("text");
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
            fpath = filesdir + afile.fname + file_suffixes.get("timed");
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

        f_repo = new FileRepository(getApplication());
    }

    public void playMediaPlayer() {
        mediaButton.setImageResource(R.drawable.pause);
        if (seekbar_runnable != null) {
            h_main.removeCallbacks(seekbar_runnable);
            h_main.removeCallbacks(scroll_runnable);
        }
        seekbar_runnable = new Runnable() {
            @Override
            public void run() {
                int cur_time_ms = mPlayer.getCurrentPosition();
                mSeekBar.setProgress(cur_time_ms);
                h_main.postDelayed(this, 25);
            }
        };
//        scroll_runnable = new Runnable() {
//            @Override
//            public void run() {
//                int cur_time_ms = mPlayer.getCurrentPosition();
//                Layout layout = ed_transtext.getLayout();
//                Log.i("APP", "IN SCROLLTHING 1");
//                int first_char_idx = layout.getLineStart(layout.getLineForVertical(ed_transtext.getBottom()));
//                String word = "";
//                for(int i = first_char_idx; i < text.length(); i++) {
//                    char c = text.charAt(i);
//                    if (word.isEmpty()) {
//                        if (c != ' ') continue;
//                    } else {
//                        if (c == ' ') break;
//                    }
//                    if (c != ' ') word += c;
//                }
//                Log.i("APP", "IN SCROLLTHING 2");
//                // get corresponding timestamp
//                int num_char_hist = 0;
//                int i = 0;
//                for(; i < original_words.size(); i++) {
//                    String w = original_words.get(i);
//                    if (num_char_hist >= first_char_idx && w.equals(word)) {
//                        break;
//                    }
//                    num_char_hist += w.length() + 1;
//                }
//                Log.i("APP", "IN SCROLLTHING 3");
//                int time_ms = word_times_ms.get(i);
//                Log.i("APP", "IN SCROLLTHING 4 " + Integer.toString(time_ms) + " " + Integer.toString(time_ms));
//                if (time_ms < cur_time_ms) {
//                    final int y = (ed_transtext.getLineCount() - 1) * ed_transtext.getLineHeight();
//                    Log.i("APP", "IN SCROLLTHING A");
//                    Runnable runnable = new Runnable() {
//                        @Override
//                        public void run() {
//                            ed_transtext.scrollTo(0, y);
//                        }
//                    };
//                    h_main.post(runnable);
//                }
//                h_main.postDelayed(this, 1000);
//            }
//        };
        h_main.post(seekbar_runnable);
//        h_main.post(scroll_runnable);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mSeekBar.setProgress(0);
                mediaButton.setImageResource(R.drawable.play);
                h_main.removeCallbacks(seekbar_runnable);
                h_main.removeCallbacks(scroll_runnable);
            }
        });
        mPlayer.start();
    }

    public void onMediaClick(View view) {
        if (is_spamclick()) return;

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
                if (h_main != null) {
                    h_main.removeCallbacks(seekbar_runnable);
                    h_main.removeCallbacks(scroll_runnable);
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        View v = getCurrentFocus();
        boolean ret = super.dispatchTouchEvent(event);
        if (v instanceof EditText) {
            View w = getCurrentFocus();
            int scrcoords[] = new int[2];
            w.getLocationOnScreen(scrcoords);
            float x = event.getRawX() + w.getLeft() - scrcoords[0];
            float y = event.getRawY() + w.getTop() - scrcoords[1];

            //Log.d("Activity", "Touch event "+event.getRawX()+","+event.getRawY()+" "+x+","+y+" rect "+w.getLeft()+","+w.getTop()+","+w.getRight()+","+w.getBottom()+" coords "+scrcoords[0]+","+scrcoords[1]);
            if (event.getAction() == MotionEvent.ACTION_UP && (x < w.getLeft() || x >= w.getRight() || y < w.getTop() || y > w.getBottom()) ) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getWindow().getCurrentFocus().getWindowToken(), 0);

                ed_transtext.clearFocus();
                ed_transtext.setFocusableInTouchMode(false);
                just_closed = true;
            }
        }
        return ret;
    }

    public void onEditClick(View view) {
        if (is_spamclick()) return;
        ed_transtext.setFocusable(true);
        ed_transtext.setFocusableInTouchMode(true);

        if (!ed_transtext.hasFocus() && !just_closed) {
            viewSwitcher.showNext();

            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ed_transtext, InputMethodManager.SHOW_IMPLICIT);
            editButton.setImageResource(R.drawable.done);

            ed_transtext.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    final String new_text = s.toString().replaceAll("(^\\s+|\\s+$)", "");
                    h_background.removeCallbacks(ed_trans_runnable);
                    ed_trans_runnable = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FileWriter fw = new FileWriter(new File(filesdir + afile.fname + file_suffixes.get("text")), false);
                                fw.write(new_text);
                                fw.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    h_background.postDelayed(ed_trans_runnable, 500);
                }
            });
        } else {
            // TODO: crashes when called on text that was edited!!
            just_closed = false;

            if (ed_trans_runnable != null) {
                h_background.removeCallbacks(ed_trans_runnable);
                ed_trans_runnable.run();
            }

            final String new_text = ed_transtext.getText().toString();
            SpannableString new_text_timed = new SpannableString(new_text);
            int idx = -1;
            int len = new_text.length();
            int i = 0;
            while(idx < len) {
                String word = (String) original_words.get(i);
                idx = new_text.indexOf(word, idx + 1);
                if (idx == -1) break;
                final int time_ms = (int) word_times_ms.get(i);
                ClickableSpanPlain span = new ClickableSpanPlain() {
                    @Override
                    public void onClick(View widget) {
                        mPlayer.seekTo(time_ms);
                        playMediaPlayer();
                    }
                };
                int idx_end = idx + word.length();
                new_text_timed.setSpan(span, idx, idx_end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i++;
            }
            tv_transtext.setText(new_text_timed);
            tv_transtext.setMovementMethod(LinkMovementMethod.getInstance());

            editButton.setImageResource(R.drawable.edit);
            viewSwitcher.showNext();
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

    public void onCopyClick(View view) {
        if (is_spamclick()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String simpletext = ed_transtext.getText().toString();
        ClipData clip = ClipData.newPlainText("Transcript", simpletext);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getApplicationContext(), "Copied", Toast.LENGTH_SHORT).show();
    }

    public void onShareClick(View view) {
        if (is_spamclick()) return;
        ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(afile.fname);
        dialog.show(getSupportFragmentManager(), "ShareDialog");
    }

    public void onDeleteClick(View view) {
        if (is_spamclick()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(FileInfo.this);
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
        f_repo.delete(afile);
        finish();
    }

    @Override
    protected void onStop() {
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
    protected void onResume() {
        super.onResume();
        handlerThread = new HandlerThread("BackgroundHandlerThread");
        handlerThread.start();
        h_background = new Handler(handlerThread.getLooper());

        fileinfo_ed_title.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(final Editable s) {
                Log.i("APP", "In text change.");
                final String cname = s.toString().replaceAll("(^\\s+|\\s+$)", "");
                final String fname = MainActivity.getFileName(cname, f_repo);
                h_background.removeCallbacks(title_runnable);

                title_runnable = new Runnable() {
                    @Override
                    public void run() {
                        Log.i("APP", "fname " + fname + " cname " + cname);
                        f_repo.rename(afile, cname, fname);
                    }
                };
                h_background.postDelayed(title_runnable, 500);

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausePlaying();
        if (title_runnable != null) {
            h_background.removeCallbacks(title_runnable);
            title_runnable.run();
        }
        if (ed_trans_runnable != null) {
            h_background.removeCallbacks(ed_trans_runnable);
            ed_trans_runnable.run();
        }
        handlerThread.quit();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(getApplicationContext(), Manage.class);
        startActivity(intent);
        finish();
    }
}

abstract class ClickableSpanPlain extends ClickableSpan {
    public void updateDrawState(TextPaint ds) {
        ds.setUnderlineText(false);
    }
}