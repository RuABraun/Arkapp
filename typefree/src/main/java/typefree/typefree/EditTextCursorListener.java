package typefree.typefree;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.util.Log;


public class EditTextCursorListener extends AppCompatEditText {
    @Nullable
    private CursorCallback callback;
    public EditTextCursorListener(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    public EditTextCursorListener(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public EditTextCursorListener(Context context) {
        super(context);
    }
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (callback != null) {
            callback.onSelectionChanged(selStart);
        }
    }
    public void setCallback(CursorCallback callback) {
        this.callback = callback;
    }
}
