package ark.ark;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Help extends Base {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        this.setTitle("Ark - Help");
    }
}
