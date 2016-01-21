package tacoball.com.geomancer;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import tacoball.com.geomancer.tacoball.com.geomancer.view.MapUpdateListener;
import tacoball.com.geomancer.tacoball.com.geomancer.view.MapUtils;

public class MapUpdaterFragment extends Fragment implements MapUpdateListener {

    //private static String TAG = "MapUpdaterFragment";

    TextView mTxvAction;
    ProgressBar mPgbAction;
    Handler mHandler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.fragment_updater, container, false);
        mTxvAction = (TextView)layout.findViewById(R.id.txvAction);
        mTxvAction.setText("檢查網路連線 ...");
        mPgbAction = (ProgressBar)layout.findViewById(R.id.pgbAction);
        mPgbAction.setProgress(0);
        mHandler = new Handler();

        // 檢查網路連線
        boolean hasNetwork = false;
        ConnectivityManager connMgr = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        for (NetworkInfo ni : connMgr.getAllNetworkInfo()) {
            if (ni.isConnected()) {
                hasNetwork = true;
                break;
            }
        }

        if (hasNetwork) {
            // 更新地圖
            MapUtils.clearUpdateNotification(getActivity());
            MapUtils.downloadMapFile(getActivity(), this);
        } else {
            // 顯示錯誤訊息
            mTxvAction.setText("沒有網路連線無法更新地圖，請打開網路後重試 ...");
            Toast.makeText(getActivity(), "無法更新地圖!!", Toast.LENGTH_SHORT).show();
        }

        return layout;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MapUtils.cancelMapUpdate();
    }

    @Override
    public void onDownload(int percent) {
        setProgress("下載圖資", percent);
        if (percent==100) {
            MapUtils.extractMapFile(getActivity(), this);
        }
    }

    @Override
    public void onDecompress(int percent) {
        setProgress("解壓縮圖資", percent);
        if (percent==100) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // TODO: need to optimize it
                    Activity activity = getActivity();
                    activity.finish();
                    activity.startActivity(new Intent(activity, MainActivity.class));
                }
            });
        }
    }

    private void setProgress(final String action, final int progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                String msg = String.format("%s: %d%%", action, progress);
                mTxvAction.setText(msg);
                mPgbAction.setProgress(progress);
            }
        });
    }

}
