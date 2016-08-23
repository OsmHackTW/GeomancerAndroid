package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.IOException;
import java.util.Locale;
import java.util.Random;

import tacoball.com.geomancer.FileUpdateManager;
import tacoball.com.geomancer.MainUtils;

/**
 * 網路連線事件觸發程式
 * 發現行動網路或 Wifi 連線時，檢查地圖與人文地理資料檔版本
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";

    // 流量管制參數
    private static final long INTERVAL    = 86400; // 兩次開啟網路的間隔時間(單位:秒)，超過此時間才允許下一次版本檢查
    private static final int  PROBABILITY = 10;    // 分流機率

    // 除錯參數
    private static final boolean ENABLE_INTERVAL    = false; // 啟用間隔限制
    private static final boolean ENABLE_PROBABILITY = false; // 啟用機率分流

    // 上次啟用網路的時間
    private static long mPrevConnected = 0;

    //
    Context mContext;
    FileUpdateManager mFum;

    //
    private int  mFileIndex;
    private long mTotalLength;

    // 偵測到網路啟用事件
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
            ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifi   = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            // TODO: Load from shared preference
            boolean allowMobile = false;
            boolean hasInternet = wifi.isConnected() || (allowMobile && mobile.isConnected());

            String msg = String.format(Locale.getDefault(), "wifi=%s, mobile=%s", wifi.isConnected(), mobile.isConnected());
            Log.d(TAG, msg);

            if (hasInternet && canCheck()) {
                Log.d(TAG, "Check this time.");
                mContext = context;
                checkVersion();
                return;
            }

            Log.d(TAG, "Skip this time.");
        }
    }

    // 檢查檔案版本
    private void checkVersion() {
        try {
            mFileIndex   = 0;
            mTotalLength = 0;
            mFum = new FileUpdateManager(MainUtils.getSavePath(mContext, mFileIndex));
            mFum.setListener(listener);
            mFum.checkVersion(MainUtils.getRemoteURL(mFileIndex));
        } catch(IOException ex) {
            Log.e(TAG, MainUtils.getReason(ex));
        }
    }

    //
    private void checkComplete() {
        String msg = "不需要更新";
        if (mTotalLength > 0) {
            double mblen = (double)mTotalLength/1048576;
            MainUtils.buildUpdateNotification(mContext, mblen);
            msg = String.format(Locale.getDefault(), "有檔案需要更新，共 %.2f MB", mblen);
        }
        Log.d(TAG, msg);

        mFum = null;
    }

    // 分流控制程式
    private boolean canCheck() {
        long curr = System.currentTimeMillis();

        // 間隔時間限制
        if (ENABLE_INTERVAL) {
            if ((curr-mPrevConnected)/1000<INTERVAL) {
                return false;
            }
            mPrevConnected = curr;
        }

        // 更新機率限制
        if (ENABLE_PROBABILITY) {
            int i = new Random(curr).nextInt(100); // 0-99
            if (i > PROBABILITY) {
                return false;
            }
        }

        return true;
    }

    private FileUpdateManager.ProgressListener listener = new FileUpdateManager.ProgressListener() {

        @Override
        public void onCheckVersion(long length, long mtime) {
            if (length>0) {
                mTotalLength += length;
            }

            mFileIndex++;
            if (mFileIndex < MainUtils.REQUIRED_FILES.length) {
                mFum.checkVersion(MainUtils.getRemoteURL(mFileIndex));
            } else {
                checkComplete();
            }
        }

        @Override
        public void onError(int step, String reason) {
            Log.e(TAG, reason);
        }

        @Override
        public void onNewProgress(int step, int percent) {
            // Unused
        }

        @Override
        public void onComplete() {
            // Unused
        }

        @Override
        public void onCancel() {
            // Unused
        }

    };

}
