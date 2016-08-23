package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

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
            // TODO: Force update flag
            // TODO: Restart Activity
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
