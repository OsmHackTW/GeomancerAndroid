package tacoball.com.geomancer;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
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
import tacoball.com.geomancer.checkupdate.CheckUpdateAdapter;

/**
 * 地圖與資料庫更新程式
 */
public class UpdateToolFragment extends Fragment {

    private static final String TAG = "UpdateToolFragment";

    // 進入主畫面前的刻意等待時間
    private static final long RESTART_DELAY = 500;

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

        // 執行一次更新流程
        update();

        return layout;
    }

    private void update() {
        Context ctx = getActivity();

        // 目錄配置
        File dbPath;
        File logPath;
        File mapPath;
        try {
            dbPath  = MainUtils.getDbPath(ctx);
            mapPath = MainUtils.getMapPath(ctx);
            logPath = MainUtils.getLogPath(ctx);
        } catch(IOException ex) {
            setMessage(ctx.getString(R.string.prompt_cannot_access_storage), true);
            return;
        }

        // 檢查應用程式是否要求更新
        final AutoUpdateManager aum = new AutoUpdateManager(logPath, dbPath);
        aum.saveTo(MainUtils.MAP_NAME, mapPath);
        boolean dataIsUseful = aum.isUseful("0.1.0");

        // 強制破壞 mtime，測試檢查更新功能再開
        // aum.damageMtime("unluckyhouse.sqlite");

        // 檢查網路連線
        boolean hasNetwork = MainUtils.isNetworkConnected(getActivity());

        if (hasNetwork) {
            if (dataIsUseful) {
                int daysFLU = aum.getDaysFromLastUpdate();
                String msg = String.format(Locale.getDefault(), "上次更新於 %d 天前", daysFLU);
                Log.e(TAG, msg);

                if (daysFLU >= 14) {
                    // 提示檢查更新
                    msg = ctx.getString(R.string.term_check_update);
                    setMessage(msg, false);

                    // 有網路 + 資料堪用 => 定期檢查更新 (先暫時做成每次都檢查)
                    // TODO: 這段 code 要改得衛生一點
                    CheckUpdateAdapter cua = new CheckUpdateAdapter() {
                        @Override
                        public void onCheck(final long totalLength, final String lastModified) {
                            if (totalLength > 0) {
                                // 有網路 + 資料堪用 + 可更新 => 交給使用者決定
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String pat = getActivity().getString(R.string.pattern_confirm_update);
                                        String msg = String.format(Locale.getDefault(), pat, getPreetySize(totalLength));
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                                        builder.setMessage(msg).setCancelable(false)
                                                .setPositiveButton(R.string.term_yes, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int id) {
                                                        aum.addListener(updateListener);
                                                        aum.start(MainUtils.getUpdateSource());
                                                    }
                                                })
                                                .setNegativeButton(R.string.term_no, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int id) {
                                                        gotoMap();
                                                    }
                                                })
                                                .show();
                                    }
                                });
                            } else {
                                // 有網路 + 資料堪用 + 免更新 => 進入應用程式
                                gotoMap();
                            }
                        }

                        @Override
                        public void onError(String reason) {
                            super.onError(reason);
                        }
                    };
                    aum.checkUpdate(MainUtils.getUpdateSource(), cua);
                } else {
                    // 有網路 + 資料堪用 + 免更新 => 進入應用程式
                    gotoMap();
                }
            } else {
                // 有網路 + 資料不堪用 => 強制更新
                aum.addListener(updateListener);
                aum.start(MainUtils.getUpdateSource());
            }
        } else {
            if (dataIsUseful) {
                // 沒網路 + 資料堪用 => 進入應用程式
                setMessage(ctx.getString(R.string.prompt_validated), false);
                gotoMap();
            } else {
                // 沒網路 + 資料不堪用 => 顯示錯誤訊息
                setMessage(ctx.getString(R.string.prompt_cannot_access_network), true);
            }
        }
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
    public void onDestroyView() {
        // 取消線上更新，並且不理會後續事件，避免 Activity 結束後閃退
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
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
                // #58 這個時機可能 App 已經被關閉，需要迴避 NPE 發生。
                // TODO: 暫時解開這個觀察一下是否會閃退，如果沒問題就移除判斷式
                Activity activity = getActivity();
                // if (activity != null) {
                activity.sendBroadcast(MainUtils.buildFragmentSwitchIntent("MAIN"));
                //}
            }
        }, RESTART_DELAY);
    }

    // 修復按鈕點擊事件，開始修復檔案與隱藏修復按鈕
    private View.OnClickListener repairListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mBtnRepair.setVisibility(View.INVISIBLE);
            update();
        }
    };

    private String getPreetySize(long bytes) {
        double mega = bytes/1048576.0;
        return String.format(Locale.getDefault(), "%.2fMB", mega);
    }

}
