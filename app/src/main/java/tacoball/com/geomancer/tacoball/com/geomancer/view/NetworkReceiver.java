package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Network Receiver for Map Update
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";
    private static final long   MIN_INTERVAL = 60;

    private static long mPrevConnected = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        long current  = System.currentTimeMillis();
        long interval = (current-mPrevConnected)/1000;
        if (interval<MIN_INTERVAL) return;

        mPrevConnected = current;

        boolean useMobile = true;
        String  action = intent.getAction();
        ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // 3G/4G/H+ ...
        if (action.equals("android.net.conn.CONNECTIVITY_CHANGE") && useMobile) {
            NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (mobile.isConnected()) {
                Log.e(TAG, "Mobile Connected.");
                checkMapUpdate();
            }
        }

        // Wifi
        if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
            NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifi.isConnected()) {
                Log.e(TAG, "Wifi Connected.");
                checkMapUpdate();
            }
        }
    }

    private void checkMapUpdate() {
        Log.e(TAG, "checkMapUpdate()");
    }

}
