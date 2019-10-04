package typefree.typefree;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class ShareConvDialogFragment extends DialogFragment {
    private static String[] files_to_share = {"Transcript", "Audio file", "Timed transcript"};
    ArrayList<Integer> mSelectedItems;

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
        mSelectedItems = new ArrayList<>(3);
        while(mSelectedItems.size() < 3) mSelectedItems.add(0);
        mSelectedItems.set(0, 1);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Choose files to share").setMultiChoiceItems(files_to_share, checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            Log.i("APP", "added " + which);
                            mSelectedItems.set(which, 1);
                        } else if (mSelectedItems.contains(which)) {
                            Log.i("APP", "removed " + which);
                            mSelectedItems.set(which, 0);
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
