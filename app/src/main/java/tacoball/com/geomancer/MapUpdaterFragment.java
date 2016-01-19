package tacoball.com.geomancer;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import tacoball.com.geomancer.tacoball.com.geomancer.view.MapUpdateListener;
import tacoball.com.geomancer.tacoball.com.geomancer.view.TaiwanMapView;

public class MapUpdaterFragment extends Fragment implements MapUpdateListener {

    private static String TAG = "MapUpdaterFragment";

    TextView mTxvAction;
    ProgressBar mPgbAction;
    Handler mHandler;

    public MapUpdaterFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final boolean TEST_UI = false;

        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.fragment_updater, container, false);

        mTxvAction = (TextView)layout.findViewById(R.id.txvAction);
        mPgbAction = (ProgressBar)layout.findViewById(R.id.pgbAction);
        mHandler   = new Handler();

        if (TEST_UI) {
            final Runnable r = new Runnable() {
                int pg = 0;
                @Override
                public void run() {
                    if (pg<100) {
                        onDownload(++pg);
                        mHandler.postDelayed(this, 200);
                    }
                }
            };

            mHandler.postDelayed(r, 2000);
        }

        Thread th = new Thread() {
            @Override
            public void run() {
                TaiwanMapView.extractMapFile(getActivity(), MapUpdaterFragment.this);
            }
        };
        th.start();

        return layout;
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

    @Override
    public void onDownload(int percent) {
        setProgress("下載圖資", percent);
    }

    @Override
    public void onDecompress(int percent) {
        setProgress("解壓縮圖資", percent);
        if (percent==100) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    getActivity().recreate();
                }
            }, 1500);
        }
    }

}
