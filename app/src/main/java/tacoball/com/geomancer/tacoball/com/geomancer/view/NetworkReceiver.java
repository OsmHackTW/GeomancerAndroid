package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.Random;

/**
 * Network Receiver for Map Update
 */
public class NetworkReceiver extends BroadcastReceiver {

    //private static final String TAG = "NetworkReceiver";
    private static final long MIN_INTERVAL = 86400; // unit seconds (debug: 10, release: 86400)
    private static final int  BINGO_RATE   = 10;    // unit percent (debug: 100, release: 10)

    private static long mPrevConnected = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        long current  = System.currentTimeMillis();
        long interval = (current-mPrevConnected)/1000;
        if (interval<MIN_INTERVAL) return;

        boolean useMobile = true; // TODO: get this from shared preferences
        String  action = intent.getAction();
        ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // 3G/4G/H+ ...
        if (action.equals("android.net.conn.CONNECTIVITY_CHANGE") && useMobile) {
            NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (mobile.isConnected() && bingo()) {
                MapUtils.checkMapVersion(context);
                mPrevConnected = current;
                return;
            }
        }

        // Wifi
        if (action.equals("android.net.wifi.WIFI_STATE_CHANGED")) {
            NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (wifi.isConnected() && bingo()) {
                MapUtils.checkMapVersion(context);
                mPrevConnected = current;
                //return;
            }
        }
    }

    private boolean bingo() {
        Random random = new Random(System.currentTimeMillis());
        int i = random.nextInt()%100; // range: 0-99
        return (i<BINGO_RATE);
    }

}
