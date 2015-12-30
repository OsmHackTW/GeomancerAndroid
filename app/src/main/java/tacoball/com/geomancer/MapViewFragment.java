package tacoball.com.geomancer;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mapsforge.map.model.MapViewPosition;

import tacoball.com.geomancer.tacoball.com.geomancer.view.TaiwanMapView;


/**
 *
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    private TextView    mTxvLocation;
    private TextView    mTxvZoom;
    private TaiwanMapView mMapView;

    public MapViewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map_view, container, false);

        // check if this is a new compilation
        Log.e(TAG, "onCreateView() ... E");

        // 配置 mapView
        mTxvZoom = (TextView)view.findViewById(R.id.zoomValue);
        mTxvLocation = (TextView)view.findViewById(R.id.location);
        mMapView = (TaiwanMapView)view.findViewById(R.id.mapView);
        mMapView.setDrawMapListener(new Runnable() {
            @Override
            public void run() {
                MapViewPosition vp = mMapView.getModel().mapViewPosition;

                String txtLoc = String.format("(%.4f, %.4f)", vp.getCenter().latitude, vp.getCenter().longitude);
                mTxvLocation.setText(txtLoc);

                String txtZoom = String.format("%s", vp.getZoomLevel());
                mTxvZoom.setText(txtZoom);
            }
        });

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mMapView.destroyAll();
    }

}
