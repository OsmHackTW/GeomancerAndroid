package tacoball.com.geomancer;

import android.app.Fragment;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.mapsforge.core.model.BoundingBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tacoball.com.geomancer.view.PointInfo;
import tacoball.com.geomancer.view.TaiwanMapView;

/**
 * 測量風水程式
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    // 介面元件
    private TaiwanMapView mMapView;     // 地圖
    private TextView      mTxvLocation; // 經緯度文字
    private TextView      mTxvZoom;     // 縮放比文字
    private TextView      mTxvAzimuth;  // 方位角文字
    private Button        mBtPosition;  // 定位按鈕
    private Button        mBtMeasure;   // 測量風水按鈕

    // 資源元件
    private SQLiteDatabase mUnluckyHouseDB;
    private SQLiteDatabase mUnluckyLaborDB;

    /**
     * 準備動作
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup mFragLayout = (ViewGroup)inflater.inflate(R.layout.fragment_map_view, container, false);

        // 狀態列
        mTxvZoom     = (TextView)mFragLayout.findViewById(R.id.txvZoomValue);
        mTxvLocation = (TextView)mFragLayout.findViewById(R.id.txvLocation);
        mTxvAzimuth  = (TextView)mFragLayout.findViewById(R.id.txvAzimuthValue);

        // 按鈕列
        mBtPosition = (Button)mFragLayout.findViewById(R.id.btPosition);
        mBtMeasure  = (Button)mFragLayout.findViewById(R.id.btMeasure);

        // POI 詳細資訊
        TextView  txvSummaryContent = (TextView)mFragLayout.findViewById(R.id.txvSummaryContent);
        TextView  txvURLContent     = (TextView)mFragLayout.findViewById(R.id.txvURLContent);
        ViewGroup vgInfoContainer   = (ViewGroup)mFragLayout.findViewById(R.id.glyPointInfo);

        // 地圖
        mMapView = (TaiwanMapView)mFragLayout.findViewById(R.id.mapView);
        mMapView.setMyLocationImage(R.drawable.arrow_up);
        mMapView.setInfoView(vgInfoContainer, txvSummaryContent, txvURLContent);

        // 事件配置
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);

        // 資料庫配置
        try {
            String path;
            path = MainUtils.getFilePath(getActivity(), 1).getAbsolutePath();
            mUnluckyHouseDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
            path = MainUtils.getFilePath(getActivity(), 2).getAbsolutePath();
            mUnluckyLaborDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return mFragLayout;
    }

    /**
     * 善後動作
     */
    @Override
    public void onDestroyView() {
        mMapView.destroyAll();

        if (mUnluckyHouseDB != null) {
            mUnluckyHouseDB.close();
        }
        if (mUnluckyLaborDB != null) {
            mUnluckyLaborDB.close();
        }

        super.onDestroyView();
    }

    /**
     * 定位與測量風水按鈕事件處理
     */
    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            // 定位
            if (v==mBtPosition) {
                mMapView.gotoMyPosition();
            }

            // 測量風水
            if (v==mBtMeasure) {
                // 查詢前準備
                int uhcnt = 0;
                int ulcnt = 0;

                BoundingBox bbox =  mMapView.getBoundingBox();

                String[] params = {
                    Double.toString(bbox.minLatitude),
                    Double.toString(bbox.maxLatitude),
                    Double.toString(bbox.minLongitude),
                    Double.toString(bbox.maxLongitude)
                };

                String sql;
                Cursor cur;

                List<PointInfo> infolist = new ArrayList<>();

                // 查詢凶宅
                sql = "SELECT id,approach,area,address,lat,lng FROM unluckyhouse " +
                      "WHERE state>1 AND lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyHouseDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    while (cur.moveToNext()) {
                        String pat     = getString(R.string.pattern_unluckyhouse_subject);
                        String url     = String.format(Locale.getDefault(), "http://unluckyhouse.com/showthread.php?t=%d", cur.getInt(0));
                        String subject = String.format(Locale.getDefault(), pat, cur.getString(1));
                        String address = cur.getString(3);
                        double lat = cur.getDouble(4);
                        double lng = cur.getDouble(5);
                        PointInfo p = new PointInfo(lat, lng, subject, R.drawable.pin_unluckyhouse, R.drawable.pin_unluckyhouse_bright);
                        p.setDescription(address);
                        p.setURL(url);
                        infolist.add(p);

                        uhcnt++;
                    }
                }
                cur.close();

                // 查詢血汗工廠
                sql = "SELECT id,exe_id,corperation,detail,ref_law,boss,exe_date,lat,lng FROM taipei " +
                      "WHERE lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyLaborDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    while (cur.moveToNext()) {
                        String pat     = getString(R.string.pattern_unluckylabor_subject);
                        String subject = String.format(Locale.getDefault(), pat, cur.getString(2));
                        String detail  = String.format(Locale.getDefault(), "%s\n%s\n%s", cur.getString(1), cur.getString(4), cur.getString(3));
                        double lat = cur.getDouble(7);
                        double lng = cur.getDouble(8);
                        PointInfo p = new PointInfo(lat, lng, subject, R.drawable.pin_unluckylabor, R.drawable.pin_unluckylabor_bright);
                        p.setDescription(detail);
                        infolist.add(p);
                        ulcnt++;
                    }
                }
                cur.close();

                // 配置 POI Marker
                if (infolist.size() > 0) {
                    PointInfo[] infoary = new PointInfo[infolist.size()];
                    infolist.toArray(infoary);
                    infolist.clear();

                    mMapView.showPoints(infoary);
                    String pat = getString(R.string.pattern_measure_result);
                    String msg = String.format(Locale.getDefault(), pat, uhcnt, ulcnt);
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), R.string.term_peace, Toast.LENGTH_SHORT).show();
                }
            }
        }

    };

    /**
     * 同步地圖狀態值 (經緯度、縮放比、方位角)，限制 Z>=15 才允許測量風水
     */
    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLoc = String.format(Locale.getDefault(), "(%.4f, %.4f)", state.cLat, state.cLng);
            mTxvLocation.setText(txtLoc);

            String txtZoom = String.format(Locale.getDefault(), "%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format(Locale.getDefault(), "%.2f", state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);

            if (state.zoom>=15) {
                mBtMeasure.setEnabled(true);
            } else {
                mBtMeasure.setEnabled(false);
            }
        }

    };

}
