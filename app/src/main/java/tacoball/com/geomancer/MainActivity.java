package tacoball.com.geomancer;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.io.File;
import java.io.IOException;

/**
 * Main Activity
 */
public class MainActivity extends ActionBarActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 配置 Android 繪圖資源，必須在 inflate 之前完成
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_main);

        // Init Fragments
        FragmentTransaction ft = getFragmentManager().beginTransaction();

        try {
            int cnt = MainUtils.REQUIRED_FILES.length;
            int exists = 0;
            for (int i=0;i<cnt;i++) {
                File required = MainUtils.getFilePath(this, i);
                if (required.exists()) {
                    exists++;
                }
            }

            if (exists<cnt) {
                ft.add(R.id.frag_container, new MapUpdaterFragment());
            } else {
                ft.add(R.id.frag_container, new MapViewFragment());
            }
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        ft.commit();
    }

}
