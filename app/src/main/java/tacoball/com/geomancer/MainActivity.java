package tacoball.com.geomancer;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.io.File;
import java.io.IOException;

/**
 * 前端程式進入點
 */
public class MainActivity extends ActionBarActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 配置 Android 繪圖資源，必須在 inflate 之前完成
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_main);

        // 配置 Fragment
        FragmentTransaction ft = getFragmentManager().beginTransaction();

        try {
            // 更新需求檢查
            int cnt = MainUtils.REQUIRED_FILES.length;
            int exists = 0;
            for (int i=0;i<cnt;i++) {
                File required = MainUtils.getFilePath(this, i);
                if (required.exists()) {
                    exists++;
                }
            }

            if (exists<cnt) {
                // 更新程式
                ft.add(R.id.frag_container, new MapUpdaterFragment());
            } else {
                // 主畫面程式
                ft.add(R.id.frag_container, new MapViewFragment());
            }
        } catch(IOException ex) {
            // MainUtils.getFilePath() 發生錯誤
            Log.e(TAG, ex.getMessage());
        }

        ft.commit();
    }

}
