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

import org.apache.commons.io.IOUtils;
import org.mapsforge.core.model.BoundingBox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    private Button         mBtPosition;
    private Button         mBtMeasure;
    private Button         mBtISee;
    //private PopupWindow    mPopResult;
    private View           mPopContent;
    private SQLiteDatabase mUnluckyDB;

    public MapViewFragment() {
        // Required empty public constructor
    }

    /*
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    */

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mFragLayout = (ViewGroup)inflater.inflate(R.layout.fragment_map_view, container, false);

        // StatusBar
        mTxvZoom     = (TextView)mFragLayout.findViewById(R.id.txvZoomValue);
        mTxvLocation = (TextView)mFragLayout.findViewById(R.id.txvLocation);
        mTxvAzimuth  = (TextView)mFragLayout.findViewById(R.id.txvAzimuthValue);

        // ButtonsBar
        mBtPosition = (Button)mFragLayout.findViewById(R.id.btPosition);
        mBtMeasure  = (Button)mFragLayout.findViewById(R.id.btMeasure);

        // Map View
        mMapView = (TaiwanMapView)mFragLayout.findViewById(R.id.mapView);
        mMapView.setMyLocationImage(R.drawable.arrow_up);

        // Events
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);

        // Popup Window
        mPopContent = getActivity().getLayoutInflater().inflate(R.layout.measure_result, null);
        mBtISee = (Button)mPopContent.findViewById(R.id.btnConfirm);
        mBtISee.setOnClickListener(mClickListener);

        // open database
        mUnluckyDB = openUnluckyHouseDB();

        // Cannot new here!!
        /*
        mPopResult = new PopupWindow(getActivity());
        mPopResult.setContentView(popContent);
        mPopResult.setBackgroundDrawable(getActivity().getResources().getDrawable(R.drawable.measure_result_background));
        mPopResult.setWidth((int)(mMapView.getWidth()*0.9));
        mPopResult.setHeight(800);
        */

        return mFragLayout;
    }

    @Override
    public void onDestroyView() {
        mMapView.destroyAll();
        if (mUnluckyDB!=null) {
            mUnluckyDB.close();
        }

        super.onDestroyView();
    }

    /**
     * Open database
     *
     * @return
     */
    private SQLiteDatabase openUnluckyHouseDB() {
        String dbname = "unluckyhouse.sqlite";

        File dir = null;
        for (File d : getActivity().getExternalFilesDirs("databases")) {
            if (d!=null) dir = d;
        }

        try {
            File f = new File(dir, dbname);
            if (!f.exists()) {
                InputStream  in  = getActivity().getAssets().open("databases/" + dbname);
                OutputStream out = new FileOutputStream(f);
                IOUtils.copy(in, out);
                in.close();
                out.close();
            }

            SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            return db;
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return null;
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
                // clear all marker

                // find unlucky houses and generate markers
                BoundingBox bbox =  mMapView.getBoundingBox();
                String sql = "SELECT id,approach,area,address,lat,lng FROM unluckyhouse " +
                             "WHERE state>1 AND "+
                             "lat>=? AND lat<=? AND lng>=? AND lng<=?";
                String[] params = {
                    Double.toString(bbox.minLatitude),
                    Double.toString(bbox.maxLatitude),
                    Double.toString(bbox.minLongitude),
                    Double.toString(bbox.maxLongitude)
                };
                Cursor cur = mUnluckyDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    int i = 0;
                    PointInfo[] info = new PointInfo[cur.getCount()];
                    while (cur.moveToNext()) {
                        Log.d(TAG, cur.getString(1) + cur.getString(2) + cur.getString(3));
                        double lat = cur.getDouble(4);
                        double lng = cur.getDouble(5);
                        String subject = String.format("凶宅(%s)", cur.getString(1));
                        info[i++] = new PointInfo(lat, lng, subject, R.drawable.pin_unlucky, R.drawable.pin_unlucky_bright);
                    }

                    mMapView.showPoints(info);
                    String msg = String.format("凶宅 %d 間", i);
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "平安無事", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Unlucky house not found");
                }

                // Cannot new it while onCreateView()
                /*
                if (mPopResult==null) {
                    mPopResult = new PopupWindow(getActivity());
                    mPopResult.setContentView(mPopContent);
                    mPopResult.setBackgroundDrawable(getActivity().getResources().getDrawable(R.drawable.measure_result_background));
                    mPopResult.setWidth((int)(mMapView.getWidth()*0.9));
                    mPopResult.setHeight(800);
                }
                mPopResult.showAtLocation(mMapView, Gravity.CENTER, 0, 0);
                */
            }

            /*
            if (v==mBtISee) {
                mPopResult.dismiss();
            }
            */
        }

    };

    /**
     * Sync map state
     */
    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLoc = String.format("(%.4f, %.4f)", state.cLat, state.cLng);
            mTxvLocation.setText(txtLoc);

            String txtZoom = String.format("%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format("%.2f", state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);

            if (state.zoom<15) {
                mBtMeasure.setEnabled(false);
            } else {
                mBtMeasure.setEnabled(true);
            }
        }

    };

}
