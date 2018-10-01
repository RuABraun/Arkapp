package ark.ark;

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
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

public class FileInfo extends Base {

    private ImageButton mediaButton;
    private MediaPlayer mPlayer;
    private Handler mHandler;
    private Handler h_main = new Handler(Looper.getMainLooper());
    private Runnable title_runnable;
    private Runnable mRunnable;
    private SeekBar mSeekBar;
    private AFile afile;
    private EditText ed_transtext;
    private EditText fileinfo_ed_title;
    private String text;
    private FileRepository f_repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fileinfo);

        afile = getIntent().getParcelableExtra("file_obj");

        mediaButton = findViewById(R.id.mediaButton);
        mHandler = new Handler();

        mSeekBar = findViewById(R.id.seekBar);

        setFileFields();
    }

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

    }

    public void setFileFields() {
        TextView tv = findViewById(R.id.file_date);
        tv.setText(afile.date);
        tv = findViewById(R.id.file_duration);
        tv.setText(String.valueOf(afile.len_s));

        String fpath = filesdir + afile.fname + file_suffixes.get("timed");
        try {
            FileInputStream fis = new FileInputStream(fpath);
            int size = fis.available();
            byte[] buffer = new byte[size];
            fis.read(buffer);
            fis.close();
            text = new String(buffer);
        } catch(IOException ex) {
            text = "Text file not found!";
        }
        ed_transtext = findViewById(R.id.ed_trans);
        ed_transtext.setText(text);

        fileinfo_ed_title = findViewById(R.id.fileinfo_ed_title);
        fileinfo_ed_title.setText(afile.title);
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
                mHandler.removeCallbacks(title_runnable);

                title_runnable = new Runnable() {
                    @Override
                    public void run() {
                        Log.i("APP", "fname " + fname + " cname " + cname);
                        f_repo.rename(afile, cname, fname);
                    }
                };
                mHandler.postDelayed(title_runnable, 2000);

            }
        });

        f_repo = new FileRepository(getApplication());
    }

    public void onMediaClick(View view) {
        if (is_spamclick()) return;

        if (!mPlayer.isPlaying()) {
            mediaButton.setImageResource(R.drawable.pause);
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    mSeekBar.setProgress(mPlayer.getCurrentPosition());
                    mHandler.postDelayed(this, 100);
                }
            };
            mHandler.post(mRunnable);
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mSeekBar.setProgress(0);
                    mediaButton.setImageResource(R.drawable.play);
                    mHandler.removeCallbacks(mRunnable);
                }
            });
            mPlayer.start();
        } else {
            mediaButton.setImageResource(R.drawable.play);
            pausePlaying();
        }

    }

    public void pausePlaying() {
        if (mPlayer != null) {
            mPlayer.pause();
            if (mHandler != null) {
                mHandler.removeCallbacks(mRunnable);
            }
        }
    }

    public void onEditClick(View view) {
        ed_transtext.setFocusable(true);
        ed_transtext.setFocusableInTouchMode(true);

        if (!ed_transtext.hasFocus()) {
            ed_transtext.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(ed_transtext, InputMethodManager.SHOW_IMPLICIT);
            Toast t = Toast.makeText(this, "Press edit button again to finish.", Toast.LENGTH_SHORT);
            t.setGravity(Gravity.TOP, 0,0);
            t.show();
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(ed_transtext.getWindowToken(), 0);
            text = ed_transtext.getText().toString();

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileWriter fw = new FileWriter(new File(filesdir + afile.fname + file_suffixes.get("timed")), false);
                        fw.write(text);
                        fw.close();
                        String normaltext = text.replaceAll("\n@[\\d.]*:?[\\d.]+\n", "");
                        fw = new FileWriter(new File(filesdir + afile.fname + file_suffixes.get("text")), false);
                        fw.write(normaltext);
                        fw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setPriority(8);
            t.start();

            ed_transtext.setText(text);
            ed_transtext.clearFocus();
        }
        ed_transtext.setFocusableInTouchMode(false);
    }

    public void onCopyClick(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        text = ed_transtext.getText().toString();
        ClipData clip = ClipData.newPlainText("Transcript", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getApplicationContext(), "Copied", Toast.LENGTH_SHORT).show();
    }

    public void onShareClick(View view) {
        ShareConvDialogFragment dialog = ShareConvDialogFragment.newInstance(afile.fname);
        dialog.show(getSupportFragmentManager(), "ShareDialog");
    }

    public void onDeleteClick(View view) {
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
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(null);
        }
        if (mPlayer != null) {
            mPlayer.pause();
            mPlayer.stop();
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (title_runnable != null) {
            mHandler.removeCallbacks(title_runnable);
            title_runnable.run();
        }
    }
}