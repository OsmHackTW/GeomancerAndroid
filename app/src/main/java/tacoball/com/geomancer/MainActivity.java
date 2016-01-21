package tacoball.com.geomancer;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import tacoball.com.geomancer.tacoball.com.geomancer.view.MapUtils;

public class MainActivity extends ActionBarActivity {

    //private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Log.e(TAG, "Activity onCreate()");
        super.onCreate(savedInstanceState);

        // 配置 Android 繪圖資源，必須在 inflate 之前完成
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_main);

        // Init Fragments
        FragmentTransaction ft = getFragmentManager().beginTransaction();

        // Extraction Test
        if (MapUtils.needToUpdate(this)) {
            //Log.e(TAG, "update mode");
            Fragment mUpdaterFrag = new MapUpdaterFragment();
            ft.add(R.id.frag_container, mUpdaterFrag);
        } else {
            //Log.e(TAG, "view mode");
            Fragment mMapFrag = new MapViewFragment();
            ft.add(R.id.frag_container, mMapFrag);
        }

        ft.commit();
    }

}
