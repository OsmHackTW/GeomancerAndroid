package tacoball.com.geomancer.view;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mapsforge.map.layer.Layers;

/**
 * 圖標群組
 */
public class PointGroup {

    //private static final String TAG = "PointGroup";

    // 多工管理寫入鎖
    private final Object WRITE_LOCK = new Object();

    private PointMarker   mPrevFocusMarker = null; // 上次選取的圖標物件
    private PointMarker[] mPointMarkers;           // 所有 POI 圖標
    private PointInfo[]   mPointInfo;              // 所有 POI 資訊
    private TextView      mTxvDescription;         // POI 詳細內容的文字方塊
    private TextView      mTxvURL;                 // POI 網址的文字方塊
    private ViewGroup     mInfoContainer;          // POI 詳細資訊框

    /**
     * 配置圖標群組
     */
    public PointGroup(Context context, Layers layers, int maxCount) {
        mPointMarkers = new PointMarker[maxCount];
        for (int i=0;i<maxCount;i++) {
            mPointMarkers[i] = new PointMarker(context, this);
            layers.add(mPointMarkers[i]);
        }
    }

    /**
     * 設定 POI 詳細資訊框
     */
    public void setInfoContainer(ViewGroup layout) {
        mInfoContainer = layout;
    }

    /**
     * 設定 POI 詳細內容的文字方塊
     */
    public void setDescriptionView(TextView txv) {
        mTxvDescription = txv;
    }

    /**
     * 設定 POI 網址的文字方塊
     */
    public void setURLView(TextView txv) {
        mTxvURL = txv;
    }

    /**
     * 更新 POI 資訊，忽略超出顯示總數上限的項目
     * Called by MapViewFragment.mBtMeasure
     *
     * @param info POI 資訊
     */
    public void setPoints(final PointInfo[] info) {
        synchronized (WRITE_LOCK) {
            int count = Math.min(mPointMarkers.length, info.length);
            for (int i = 0; i < count; i++) {
                mPointMarkers[i].update(info[i]);
            }

            mPointInfo = info;
        }
    }

    /**
     * 設定指定圖標為選取狀態
     * - 設定指定圖標為選取狀態
     * - 其餘圖標切換成一般狀態
     * - 顯示選取圖標的詳細資訊
     * Called by PointMarker.onTap() @MainThread
     *
     * @param focusMarker 指定的圖標物件
     */
    public void setFocus(final PointMarker focusMarker) {
        synchronized (WRITE_LOCK) {
            for (int i=0;i<mPointMarkers.length;i++) {
                if (focusMarker==mPointMarkers[i]) {
                    focusMarker.setFocusedPin();
                    mTxvDescription.setText(mPointInfo[i].getDescription());
                    if (mPointInfo[i].getURL().equals("")) {
                        mTxvURL.setText("(無)");
                    } else {
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
