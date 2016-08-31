package tacoball.com.geomancer.checkupdate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

import tacoball.com.geomancer.MainUtils;

/**
 * 網路連線事件觸發程式
 * 發現行動網路或 Wifi 連線時，檢查地圖與人文地理資料檔版本
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";

    // 流量管制參數
    private static final long REPEATED    = 5;     // 5 秒內再觸發事件視為重複
    private static final long INTERVAL    = 86400; // 每天只檢查一次更新
    private static final int  PROBABILITY = 20;    // 20% 更新機率，沒更新的明天請早，用來分散流量

    // 除錯參數
    public static final boolean ENABLE_INTERVAL    = true; // 啟用間隔限制
    public static final boolean ENABLE_PROBABILITY = true; // 啟用機率分流

    // 上次啟用網路的時間
    private static long mPrevConnected = 0;
    private static long mPrevOverInterval = 0;

    // 資源元件
    Context mContext;
    FileUpdateManager mFum;

    // 狀態值
    private int  mFileIndex;
    private long mTotalLength;

    // 偵測到網路啟用事件
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
            ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifi   = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            boolean hasInternet = wifi.isConnected() || (MainUtils.canUpdateByMobile(context) && mobile.isConnected());

            // 網路可使用也允許更新時，檢查需要更新的檔案
            if (hasInternet && canCheck()) {
                mContext = context;
                checkVersion();
            }
        }
    }

    // 檢查檔案版本
    private void checkVersion() {
        try {
            mFileIndex   = 0;
            mTotalLength = 0;
            mFum = new FileUpdateManager();

            int cnt = 0;
            for (int i=0; i<MainUtils.REQUIRED_FILES.length; i++) {
                File saveTo   = MainUtils.getSavePath(mContext, i);
                long mtimeMin = MainUtils.REQUIRED_MTIME[i];
                if (mFum.isRequired(MainUtils.getRemoteURL(i), saveTo, mtimeMin)) {
                    cnt++;
                }
            }

            if (cnt==0) {
                File saveTo = MainUtils.getSavePath(mContext, mFileIndex);
                mFum.setListener(listener);
                mFum.checkVersion(MainUtils.getRemoteURL(mFileIndex), saveTo);
            } else {
                Log.w(TAG, "需要強制更新，不顯示系統通知");
            }
        } catch(IOException ex) {
            Log.e(TAG, MainUtils.getReason(ex));
        }
    }

    // 傳輸量計算完成後處理
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
        long secdiff;

        // 防止重複事件
        // 行動網路開啟後，網路連線事件會連續觸發 4~5 個，不確定是系統問題還是手機問題
        secdiff = (curr-mPrevConnected)/1000;
        if (secdiff<REPEATED) {
            Log.d(TAG, "Repeated event.");
            return false;
        }
        mPrevConnected = curr;

        // 間隔時間限制
        if (ENABLE_INTERVAL) {
            secdiff = (curr-mPrevOverInterval)/1000;
            if (secdiff<INTERVAL) {
                Log.d(TAG, "Limit by interval.");
                return false;
            }
            mPrevOverInterval = curr;
        }

        // 更新機率限制
        if (ENABLE_PROBABILITY) {
            int i = new Random(curr).nextInt(100); // 0-99
            if (i > PROBABILITY) {
                Log.d(TAG, "Limit by probability.");
                return false;
            }
        }

        return true;
    }

    // 待更新項目傳輸量計算
    private FileUpdateManager.ProgressListener listener = new FileUpdateManager.ProgressListener() {

        @Override
        public void onCheckVersion(long length, long mtime) {
            if (length>0) {
                mTotalLength += length;
            }

            mFileIndex++;
            if (mFileIndex < MainUtils.REQUIRED_FILES.length) {
                try {
                    File saveTo = MainUtils.getSavePath(mContext, mFileIndex);
                    mFum.checkVersion(MainUtils.getRemoteURL(mFileIndex), saveTo);
                } catch(IOException ex) {
                    Log.e(TAG, MainUtils.getReason(ex));
                }
            } else {
                checkComplete();
            }
        }

        @Override
        public void onError(int step, String reason) {
            Log.e(TAG, reason);
        }

    };

}
