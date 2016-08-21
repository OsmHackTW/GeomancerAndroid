package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * 取消更新動作
 */
public class CancelUpdateService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //MapUtils.clearUpdateNotification(this);
        return 0;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
