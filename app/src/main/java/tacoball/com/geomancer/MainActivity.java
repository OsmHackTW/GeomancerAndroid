package tacoball.com.geomancer;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.util.List;
import java.util.Locale;

import io.fabric.sdk.android.Fabric;
import tacoball.com.geomancer.checkupdate.NetworkReceiver;
import tacoball.com.geomancer.view.TaiwanMapView;

/**
 * 前端程式進入點
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final boolean SIMULATE_OLD_MTIME = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());

        // 配置 Android 繪圖資源，必須在 inflate 之前完成
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_main);
        Log.d(TAG, "啟動");

        // 配置廣播接收器
        this.registerReceiver(receiver, MainUtils.buildFragmentSwitchIntentFilter());

        // 清理儲存空間
        MainUtils.cleanStorage(this);

        changeFragment(new UpdateToolFragment());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    // 地毯式檢查用到的除錯參數
    private void checkDebugParameters() {
        int cnt = 0;

        if (!NetworkReceiver.ENABLE_INTERVAL) {
            Log.w(TAG, getString(R.string.log_disable_interval_limit));
            cnt++;
        }

        if (!NetworkReceiver.ENABLE_PROBABILITY) {
            Log.w(TAG, getString(R.string.log_disable_probability_limit));
            cnt++;
        }

        if (MainUtils.MIRROR_NUM != 0) {
            Log.w(TAG, getString(R.string.log_use_debugging_mirror));
            cnt++;
        }

        if (TaiwanMapView.SEE_DEBUGGING_POINT) {
            Log.w(TAG, getString(R.string.log_see_debugging_point));
            cnt++;
        }

        if (SIMULATE_OLD_MTIME) {
            Log.w(TAG, getString(R.string.log_simulate_old_mtime));
            cnt++;
        }

        if (cnt > 0) {
            String pat = getString(R.string.pattern_enable_debugging);
            String msg = String.format(Locale.getDefault(), pat, cnt);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // 切換 Fragment
    private void changeFragment(Fragment nextFrag) {
        Log.d(TAG, nextFrag.getClass().getSimpleName());

        List<Fragment> frags = getSupportFragmentManager().getFragments();
        if (frags != null) {
            for (Fragment prevFrag : frags) {
                if (prevFrag != null && prevFrag.getClass() == nextFrag.getClass()) {
                    Log.d(TAG, "Exists this fragment, skip replacement.");
                    return;
                }
            }
        }

        FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
        trans.replace(R.id.frag_container, nextFrag);
        trans.commit();
    }

    // 廣播接收器，處理使用者更新要求用
    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = String.format(Locale.getDefault(), "Got broadcast intent action=%s", intent.getAction());
            Log.d(TAG, msg);

            if (intent.getAction().equals("MAIN")) {
                MainUtils.clearUpdateRequest(MainActivity.this);
                changeFragment(new MapViewFragment());
            }

            if (intent.getAction().equals("UPDATE")) {
                MainUtils.clearUpdateRequest(MainActivity.this);
                changeFragment(new UpdateToolFragment());
            }

            if (intent.getAction().equals("CONTRIBUTORS")) {
                // W/MapWorkerPool: Shutdown workers executor failed (MapWorkerPool.java:125)
                Fragment f = new SimpleFragment();
                Bundle args = new Bundle();
                args.putInt("LAYOUT_ID", R.layout.fragment_contributors);
                f.setArguments(args);
                changeFragment(f);
            }

            if (intent.getAction().equals("LICENSE")) {
                // W/MapWorkerPool: Shutdown workers executor failed (MapWorkerPool.java:125)
                Fragment f = new SimpleFragment();
                Bundle args = new Bundle();
                args.putInt("LAYOUT_ID", R.layout.fragment_license);
                f.setArguments(args);
                changeFragment(f);
            }
        }

    };

}
