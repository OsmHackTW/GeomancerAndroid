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

import tacoball.com.geomancer.tacoball.com.geomancer.view.PointInfo;
import tacoball.com.geomancer.tacoball.com.geomancer.view.TaiwanMapView;

/**
 * Main UI
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    private ViewGroup      mFragLayout;
    private TaiwanMapView  mMapView;
    private TextView       mTxvLocation;
    private TextView       mTxvZoom;
    private TextView       mTxvAzimuth;
    private TextView       mTxvSummaryContent;
    private TextView       mTxvURLContent;
    private ViewGroup      mInfoContainer;
    private Button         mBtPosition;
    private Button         mBtMeasure;
    private SQLiteDatabase mUnluckyHouseDB;
    private SQLiteDatabase mUnluckyLaborDB;

    public MapViewFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mFragLayout = (ViewGroup)inflater.inflate(R.layout.fragment_map_view, container, false);

        // StatusBar
        mTxvZoom     = (TextView)mFragLayout.findViewById(R.id.txvZoomValue);
        mTxvLocation = (TextView)mFragLayout.findViewById(R.id.txvLocation);
        mTxvAzimuth  = (TextView)mFragLayout.findViewById(R.id.txvAzimuthValue);
        mTxvSummaryContent = (TextView)mFragLayout.findViewById(R.id.txvSummaryContent);
        mTxvURLContent     = (TextView)mFragLayout.findViewById(R.id.txvURLContent);
        mInfoContainer = (ViewGroup)mFragLayout.findViewById(R.id.glyPointInfo);

        // ButtonsBar
        mBtPosition = (Button)mFragLayout.findViewById(R.id.btPosition);
        mBtMeasure  = (Button)mFragLayout.findViewById(R.id.btMeasure);

        // Map View
        mMapView = (TaiwanMapView)mFragLayout.findViewById(R.id.mapView);
        mMapView.setMyLocationImage(R.drawable.arrow_up);
        mMapView.setInfoView(mInfoContainer, mTxvSummaryContent, mTxvURLContent);

        // Events
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);

        // open database
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
     * handle event of buttons
     */
    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (v==mBtPosition) {
                mMapView.gotoMyPosition();
            }

            if (v==mBtMeasure) {
                // find unlucky houses and generate markers
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

                sql = "SELECT id,approach,area,address,lat,lng FROM unluckyhouse " +
                      "WHERE state>1 AND lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyHouseDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    while (cur.moveToNext()) {
                        String url     = String.format(Locale.getDefault(), "http://unluckyhouse.com/showthread.php?t=%d", cur.getInt(0));
                        String subject = String.format(Locale.getDefault(), "凶宅 (%s)", cur.getString(1));
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

                sql = "SELECT id,exe_id,corperation,detail,ref_law,boss,exe_date,lat,lng FROM taipei " +
                      "WHERE lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyLaborDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    while (cur.moveToNext()) {
                        String subject = String.format(Locale.getDefault(), "血汗工廠 (%s)", cur.getString(2));
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

                if ((uhcnt+ulcnt) > 0) {
                    PointInfo[] infoary = new PointInfo[infolist.size()];
                    infolist.toArray(infoary);
                    infolist.clear();

                    mMapView.showPoints(infoary);
                    String msg = String.format(Locale.getDefault(), "凶宅 %d 間、血汗工廠 %d 間", uhcnt, ulcnt);
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "平安無事", Toast.LENGTH_SHORT).show();
                }
            }
        }

    };

    /**
     * Sync map state
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

            if (state.zoom<15) {
                mBtMeasure.setEnabled(false);
            } else {
                mBtMeasure.setEnabled(true);
            }
        }

    };

}
