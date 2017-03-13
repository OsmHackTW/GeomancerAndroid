package tacoball.com.geomancer;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.checkupdate.AutoUpdateAdapter;
import tacoball.com.geomancer.checkupdate.AutoUpdateManager;

/**
 * 地圖與資料庫更新程式
 */
public class UpdateToolFragment extends Fragment {

    private static final String TAG = "MapUpdaterFragment";

    // 進入主畫面前的刻意等待時間
    private static final long RESTART_DELAY = 1500;

    // 介面元件
    TextView    mTxvAction; // 步驟說明文字
    ProgressBar mPgbAction; // 進度條
    Button      mBtnRepair; // 修復按鈕
    Handler     mHandler;   // 介面動作轉送

    /**
     * 準備動作
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.fragment_updater, container, false);

        mTxvAction = (TextView)layout.findViewById(R.id.txvAction);

        mPgbAction = (ProgressBar)layout.findViewById(R.id.pgbAction);
        mPgbAction.setProgress(0);

        mBtnRepair = (Button)layout.findViewById(R.id.btnRepair);
        mBtnRepair.setVisibility(View.INVISIBLE);
        mBtnRepair.setOnClickListener(repairListener);

        mHandler = new Handler();

        // 設定版本字串
        try {
            PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            TextView txvVersion = (TextView)layout.findViewById(R.id.txvVersion);
            txvVersion.setText(packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }

        Context ctx = getActivity();

        // 檢查使用者是否要求更新
        boolean userRequest = MainUtils.hasUpdateRequest(ctx);
        MainUtils.clearUpdateRequest(ctx);

        // 目錄配置
        File dbPath;
        File logPath;
        File mapPath;
        try {
            dbPath  = MainUtils.getDbPath(ctx);
            mapPath = MainUtils.getMapPath(ctx);
            logPath = MainUtils.getLogPath(ctx);
        } catch(IOException ex) {
            mTxvAction.setText(R.string.prompt_cannot_access_storage);
            return layout;
        }

        // 檢查應用程式是否要求更新
        AutoUpdateManager aum = new AutoUpdateManager(logPath, dbPath);
        aum.saveTo(MainUtils.MAP_NAME, mapPath);
        boolean appRequest = !aum.isUseful("0.1.0");

        // 檢查網路連線
        boolean hasNetwork = false;
        ConnectivityManager connMgr = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        for (NetworkInfo ni : connMgr.getAllNetworkInfo()) {
            if (ni.isConnected()) {
                hasNetwork = true;
                break;
            }
        }

        // 有必要更新時，執行更新作業
        if (appRequest || userRequest) {
            if (hasNetwork) {
                aum.addListener(updateListener);
                aum.start(MainUtils.getUpdateSource());
                if (appRequest) {
                    Log.e(TAG, "應用程式要求更新");
                } else {
                    Log.e(TAG, "使用者要求更新");
                }
            } else {
                setMessage(ctx.getString(R.string.prompt_cannot_access_network), false);
                Log.e(TAG, "無法更新");
            }
        } else {
            // 延遲一小段時間後直接進入主畫面
            setMessage(ctx.getString(R.string.prompt_validated), false);
            gotoMap();
            Log.e(TAG, "不需要更新");
        }

        return layout;
    }

    /**
     * 更新事件接收器
     */
    private AutoUpdateAdapter updateListener = new AutoUpdateAdapter() {

        @Override
        public void onFileTransfer(String filename, int percent) {
            String step = String.format(Locale.getDefault(), "下載檔案 %s", filename);
            setProgress(step, percent);
        }

        @Override
        public void onFileExtract(String filename, int percent) {
            String step = String.format(Locale.getDefault(), "解壓縮檔案 %s", filename);
            setProgress(step, percent);
        }

        @Override
        public void onError(String reason) {
            setMessage(reason, true);
        }

        @Override
        public void onComplete() {
            gotoMap();
            setMessage("更新完成", false);
        }

    };

    /**
     * 善後動作
     */
    @Override
    public void onDestroy() {
        // 取消線上更新，並且不理會後續事件，避免 Activity 結束後閃退
        // TODO

        super.onDestroy();
    }

    /**
     * 顯示新進度
     *
     * @param step     步驟
     * @param progress 進度
     */
    private void setProgress(final String step, final int progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String msg = String.format(Locale.getDefault(), "%s %d%%", step, progress);
                mTxvAction.setText(msg);
                mPgbAction.setProgress(progress);
            }
        });
    }

    /**
     * 顯示錯誤訊息與修復按鈕
     *
     * @param msg 錯誤訊息
     */
    private void setMessage(final String msg, final boolean isError) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTxvAction.setText(msg);
                if (isError) {
                    mBtnRepair.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * 重新啟動 App
     */
    private void gotoMap() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Switch fragment
                getActivity().sendBroadcast(MainUtils.buildFragmentSwitchIntent("MAIN"));
            }
        }, RESTART_DELAY);
    }

    // 修復按鈕點擊事件，開始修復檔案與隱藏修復按鈕
    private View.OnClickListener repairListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mBtnRepair.setVisibility(View.INVISIBLE);
            // TODO: 暫不實作
        }
    };

}
