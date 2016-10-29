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

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import tacoball.com.geomancer.checkupdate.FileUpdateManager;
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

        // 配置 Android 繪圖資源，必須在 inflate 之前完成
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_main);

        // 配置廣播接收器
        this.registerReceiver(receiver, MainUtils.buildFragmentSwitchIntentFilter());

        // 清理儲存空間
        MainUtils.cleanStorage(this);

        try {
            // 使用者要求更新檢查
            boolean userRequest = MainUtils.hasUpdateRequest(this);
            MainUtils.clearUpdateRequest(this);

            // 模擬第二個檔案太舊
            if (SIMULATE_OLD_MTIME) {
                File f = MainUtils.getFilePath(this, 2);
                long otime = MainUtils.REQUIRED_MTIME[2] - 86400000;
                if (!f.setLastModified(otime)) {
                    Log.e(TAG, "Cannot set mtime.");
                }
            }

            // 必要檔案更新檢查
            int cnt = 0;
            FileUpdateManager fum = new FileUpdateManager();
            for (int i=0; i<MainUtils.REQUIRED_FILES.length; i++) {
                File saveTo   = MainUtils.getSavePath(this, i);
                long mtimeMin = MainUtils.REQUIRED_MTIME[i];
                if (fum.isRequired(MainUtils.getRemoteURL(i), saveTo, mtimeMin)) {
                    String msg = String.format(Locale.getDefault(), "檔案 %d 需要強制更新", i);
                    Log.d(TAG, msg);
                    cnt++;
                }
            }
            boolean needRequirements = (cnt>0);

            if (needRequirements || userRequest) {
                // 更新程式
                changeFragment(new UpdateToolFragment());
            } else {
                // 主畫面程式
                changeFragment(new MapViewFragment());
            }
        } catch(IOException ex) {
            // MainUtils.getFilePath() 發生錯誤
            Log.e(TAG, ex.getMessage());
        } finally {
            checkDebugParameters();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    // 地毯式檢查用到的除錯參數
    private void checkDebugParameters() {
        int cnt = 0;

        if (FileUpdateManager.forceDownloadFailed) {
            Log.w(TAG, getString(R.string.log_simulate_download_failed));
            cnt++;
        }

        if (FileUpdateManager.forceRepairFailed) {
            Log.w(TAG, getString(R.string.log_simulate_repair_failed));
            cnt++;
        }

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
                Fragment f = new SimpleFragment();
                Bundle args = new Bundle();
                args.putInt("LAYOUT_ID", R.layout.fragment_contributors);
                f.setArguments(args);
                changeFragment(f);
            }

            if (intent.getAction().equals("LICENSE")) {
                Fragment f = new SimpleFragment();
                Bundle args = new Bundle();
                args.putInt("LAYOUT_ID", R.layout.fragment_license);
                f.setArguments(args);
                changeFragment(f);
            }
        }

    };

}
