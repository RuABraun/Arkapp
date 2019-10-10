package typefree.typefree;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.design.chip.ChipGroup;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

public class TipDialog extends DialogFragment {
    String title;
    String message;
    String knows_tag;
    TextView tv_title, tv_msg;
    CheckBox checkBox;
    Button dialog_button;
    Dialog dialog;

    public static TipDialog newInstance(String title_, String message_, String tag) {
        TipDialog frag = new TipDialog();
        Bundle args = new Bundle();
        args.putString("title", title_);
        args.putString("message", message_);
        args.putString("tag", tag);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MainActivity act = (MainActivity) getActivity();
        dialog = new Dialog(act);
        dialog.setContentView(R.layout.dialog);
        message = getArguments().getString("message");
        title = getArguments().getString("title");
        knows_tag = getArguments().getString("tag");

        tv_title = dialog.findViewById(R.id.tv_title);
        tv_msg = dialog.findViewById(R.id.tv_msg);
        tv_title.setText(title);
        tv_msg.setText(message);
        checkBox = dialog.findViewById(R.id.checkBox);
        checkBox.setChecked(false);
        dialog_button = dialog.findViewById(R.id.dialog_ok_button);
        dialog_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkBox.isChecked()) {
                    act.settings.edit().putBoolean(knows_tag, true).apply();
                }
                dialog.dismiss();
            }
        });
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dialog;
    }
}
