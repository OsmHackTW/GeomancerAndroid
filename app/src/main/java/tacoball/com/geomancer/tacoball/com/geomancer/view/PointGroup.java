package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.Context;

import org.mapsforge.map.layer.Layers;

/**
 *
 */
public class PointGroup {

    private static final String TAG = "PointGroup";

    private PointMarker   mPrevFocusMarker = null;
    private PointMarker[] mPointMarkers;
    private PointInfo[]   mPointInfo;

    public PointGroup(Context context, Layers layers, int maxCount) {
        mPointMarkers = new PointMarker[maxCount];
        for (int i=0;i<maxCount;i++) {
            mPointMarkers[i] = new PointMarker(context, this);
            layers.add(mPointMarkers[i]);
        }
    }

    public void clear() {
        for (int i=0;i<mPointMarkers.length;i++) {
            mPointMarkers[i].setVisible(false, true);
        }
        mPrevFocusMarker = null;
    }

    public void setPoints(PointInfo[] info) {
        int count = Math.min(mPointMarkers.length, info.length);
        for (int i=0;i<count;i++) {
            mPointMarkers[i].update(info[i]);
        }

        mPointInfo = info;
    }

    public void setFocus(PointMarker focusMarker) {
        for (int i=0;i<mPointMarkers.length;i++) {
            if (focusMarker==mPointMarkers[i]) {
                focusMarker.setFocusedPin();
                continue;
            }
            if (mPrevFocusMarker==mPointMarkers[i]) {
                mPrevFocusMarker.setNormalPin();
            }
        }

        mPrevFocusMarker = focusMarker;
    }

}
