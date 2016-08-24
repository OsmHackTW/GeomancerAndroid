package tacoball.com.geomancer.checkupdate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import tacoball.com.geomancer.MainActivity;
import tacoball.com.geomancer.MainUtils;

/**
 * 取消更新動作
 */
public class ConfirmUpdateService extends Service {

    private static final String TAG = "ConfirmUpdateService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MainUtils.clearUpdateNotification(this);
        if (intent.getAction().equals("Yes")) {
            Log.d(TAG, "User accept updating.");

            // 用於 App 開啟狀態
            Intent itBroadcast = new Intent();
            itBroadcast.setAction("UPDATE");
            sendBroadcast(itBroadcast);

            // 用於 App 關閉狀態
            // 如果 App 已開啟，由於主程式採用 singletop 設定，這個方法沒有效果
            MainUtils.setUpdateRequest(this);
            Intent itStart = new Intent(this, MainActivity.class);
            itStart.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(itStart);
        } else {
            Log.d(TAG, "User deny updating.");
        }
        return 0;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
