package ark.ark;

import android.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.IOException;

import static ark.ark.Base.filesdir;

public class FileFragment extends DialogFragment{
    private TextView close_view;
    private AFile afile;
    private String title;
    private String date;
    private String text;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.file_view, container, false);

        TextView tv = view.findViewById(R.id.file_title);
        tv.setText(title);
        tv = view.findViewById(R.id.file_date);
        tv.setText(date);

        tv = view.findViewById(R.id.clickable_text_view);
        tv.setText(text);


        return view;
    }

    public void setFileFields(AFile af) {

        title = af.fname;
        date = af.date;

        String fpath = filesdir + title + ".txt";
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
    }

    @Override
    public void onResume() {
        super.onResume();
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width = (int) ((float)dm.widthPixels * 0.95);
        int height = (int) ((float)dm.heightPixels * 0.8);
        getDialog().getWindow().setLayout(width, height);
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}
