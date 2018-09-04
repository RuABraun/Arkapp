package ark.ark;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;


public class NameConvDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View v = inflater.inflate(R.layout.dialog_namefile, null);
        builder.setTitle("Name the recording")
                .setView(v)
                .setPositiveButton("DONE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditText ed = getDialog().findViewById(R.id.dialog_ed);
                        String cname = ed.getText().toString();
                        MainActivity act = (MainActivity) getActivity();
                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(ed.getWindowToken(), 0);
                        act.onConvNameSelection(cname);
                        dismiss();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        EditText ed = getDialog().findViewById(R.id.dialog_ed);
                        String cname = ed.getText().toString();
                        MainActivity act = (MainActivity) getActivity();
                        act.onConvNameSelection(cname);
                    }
                });
        final EditText ed = v.findViewById(R.id.dialog_ed);
        ed.requestFocus();
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        return builder.create();
    }
}