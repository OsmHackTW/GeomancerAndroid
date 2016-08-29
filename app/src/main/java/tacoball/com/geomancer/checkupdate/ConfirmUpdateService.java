package tacoball.com.geomancer.checkupdate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import tacoball.com.geomancer.MainActivity;
import tacoball.com.geomancer.MainUtils;

/**
 * 更新通知的後續處理服務
 */
public class ConfirmUpdateService extends Service {

    private static final String TAG = "ConfirmUpdateService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MainUtils.clearUpdateNotification(this);

        // 在 Xperia Z5 上 Service 可能被不預期的程式啟動，導致 action 為 null 因此發生 NPE
        // 網路開啟時 App 正好關閉中
        String action = intent.getAction();
        if (action!=null && action.equals("Yes")) {
            // 使用者要更新
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
            // 使用者鼻要更新
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
