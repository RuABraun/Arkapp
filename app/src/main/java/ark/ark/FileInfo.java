package ark.ark;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileInfo extends Base {

    private ImageButton mediaButton;
    private MediaPlayer mPlayer;
    private Handler mHandler;
    private Runnable mRunnable;
    private SeekBar mSeekBar;
    private AFile afile;
    private String text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fileinfo);

        afile = getIntent().getParcelableExtra("file_obj");
        setFileFields();

        mediaButton = findViewById(R.id.mediaButton);
        mHandler = new Handler();

        mSeekBar = findViewById(R.id.seekBar);

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
        TextView tv = findViewById(R.id.file_title);
        tv.setText(afile.title);
        tv = findViewById(R.id.file_date);
        tv.setText(afile.date);
        tv = findViewById(R.id.file_duration);
        tv.setText(String.valueOf(afile.len_s));

        String fpath = filesdir + afile.fname + ".txt";
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
        tv = findViewById(R.id.clickable_text_view);
        tv.setText(text);
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

}