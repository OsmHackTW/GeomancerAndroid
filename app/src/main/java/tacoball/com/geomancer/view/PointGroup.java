package tacoball.com.geomancer.view;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mapsforge.map.layer.Layers;

/**
 *
 */
public class PointGroup {

    //private static final String TAG = "PointGroup";

    private PointMarker   mPrevFocusMarker = null;
    private PointMarker[] mPointMarkers;
    private PointInfo[]   mPointInfo;
    private TextView      mTxvDescription;
    private TextView      mTxvURL;
    private ViewGroup     mInfoContainer;

    private final Object WRITE_LOCK = new Object();

    public PointGroup(Context context, Layers layers, int maxCount) {
        mPointMarkers = new PointMarker[maxCount];
        for (int i=0;i<maxCount;i++) {
            mPointMarkers[i] = new PointMarker(context, this);
            layers.add(mPointMarkers[i]);
        }
    }

    public void setInfoContainer(ViewGroup layout) {
        mInfoContainer = layout;
    }

    public void setDescriptionView(TextView txv) {
        mTxvDescription = txv;
    }

    public void setURLView(TextView txv) {
        mTxvURL = txv;
    }

    /*
    public void clear() {
        synchronized (mPointMarkers) {
            for (int i = 0; i < mPointMarkers.length; i++) {
                mPointMarkers[i].setVisible(false, true);
            }
            mPrevFocusMarker = null;
            mInfoContainer.setVisibility(ViewGroup.INVISIBLE);
        }
    }
    */

    public void setPoints(final PointInfo[] info) {
        // Called by MapViewFragment.mBtMeasure
        synchronized (WRITE_LOCK) {
            int count = Math.min(mPointMarkers.length, info.length);
            for (int i = 0; i < count; i++) {
                mPointMarkers[i].update(info[i]);
            }

            mPointInfo = info;
        }
    }

    public void setFocus(final PointMarker focusMarker) {
        // Called by PointMarker.onTap() @MainThread
        synchronized (WRITE_LOCK) {
            for (int i=0;i<mPointMarkers.length;i++) {
                if (focusMarker==mPointMarkers[i]) {
                    focusMarker.setFocusedPin();
                    mTxvDescription.setText(mPointInfo[i].getDescription());
                    if (mPointInfo[i].getURL().equals("")) {
                        //mTxvURL.setVisibility(View.INVISIBLE);
                        mTxvURL.setText("(無)");
                    } else {
                        //mTxvURL.setVisibility(View.VISIBLE);
                        mTxvURL.setText(mPointInfo[i].getURL());
                    }
                    mInfoContainer.setVisibility(ViewGroup.VISIBLE);
                    continue;
                }
                if (mPrevFocusMarker==mPointMarkers[i]) {
                    mPrevFocusMarker.setNormalPin();
                }
            }
        }

        mPrevFocusMarker = focusMarker;
    }

}