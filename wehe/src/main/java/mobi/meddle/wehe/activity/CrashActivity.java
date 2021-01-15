package mobi.meddle.wehe.activity;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import mobi.meddle.wehe.R;

/**
 * @author Alankrit Joshi
 * Crash controller on an event of a crash
 * XML layout: activity_crash.xml
 */
public class CrashActivity extends AppCompatActivity {

    /**
     * @param savedInstanceState On creation of the instances
     *                           1. Binds content view with 'layout/crash_layout.xml'
     *                           2. Initializes mToolbar
     * @author Alankrit Joshi
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Bind content view with 'layout/crash_layout.xml'
        setContentView(R.layout.crash_layout);
        // Initialize mToolbar using the element in the layout
        // Toolbar data type to set mToolbar to show the title of the activity
        Toolbar toolbar = findViewById(R.id.crash_bar);
        // Enable support action bar using the mToolbar
        setSupportActionBar(toolbar);
        // If support action bar was enabled properly, set title and enable navigation
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.error_page_title));
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            CrashActivity.this.finish();
            CrashActivity.this.overridePendingTransition(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right);
        }
        return super.onKeyDown(keyCode, event);
    }
}
