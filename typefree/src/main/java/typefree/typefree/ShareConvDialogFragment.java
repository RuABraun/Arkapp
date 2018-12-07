package typefree.typefree;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import java.util.ArrayList;
import java.util.Arrays;

public class ShareConvDialogFragment extends DialogFragment {
    private static String[] files_to_share = {"Transcript", "Audio file", "Timed transcript"};
    ArrayList mSelectedItems;

    public static ShareConvDialogFragment newInstance(String fname) {
        ShareConvDialogFragment frag = new ShareConvDialogFragment();
        Bundle args = new Bundle();
        args.putString("fname", fname);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String fname = getArguments().getString("fname");

        final boolean[] checked = {true, false, false};
        mSelectedItems = new ArrayList<>(Arrays.asList(0));
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Choose files to share").setMultiChoiceItems(files_to_share, checked ,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            mSelectedItems.add(which);
                        } else if (mSelectedItems.contains(which)) {
                            mSelectedItems.remove(Integer.valueOf(which));
                        }
                    }
                })
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Base act = (Base) getActivity();
                        act.share(fname, mSelectedItems);
                        dismiss();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                });

        return builder.create();
    }
}
