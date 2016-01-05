package tacoball.com.geomancer;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import tacoball.com.geomancer.tacoball.com.geomancer.view.TaiwanMapView;

/**
 *
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    private TaiwanMapView mMapView;
    private TextView      mTxvLocation;
    private TextView      mTxvZoom;
    private TextView      mTxvAzimuth;
    private Button        mBtPosition;
    private Button        mBtMeasure;

    public MapViewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup)inflater.inflate(R.layout.fragment_map_view, container, false);

        // StatusBar
        mTxvZoom     = (TextView)layout.findViewById(R.id.txvZoomValue);
        mTxvLocation = (TextView)layout.findViewById(R.id.txvLocation);
        mTxvAzimuth  = (TextView)layout.findViewById(R.id.txvAzimuthValue);

        // ButtonsBar
        mBtPosition = (Button)layout.findViewById(R.id.btPosition);
        mBtMeasure  = (Button)layout.findViewById(R.id.btMeasure);

        // Map View
        mMapView = (TaiwanMapView)layout.findViewById(R.id.mapView);
        mMapView.setMyLocationImage(R.drawable.arrow_up);

        // Events
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);

        return layout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mMapView.destroyAll();
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (v== mBtPosition) {
                mMapView.gotoMyPosition();
            }

            if (v==mBtMeasure) {
                // TODO
            }
        }

    };

    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLoc = String.format("(%.4f, %.4f)", state.cLat, state.cLng);
            mTxvLocation.setText(txtLoc);

            String txtZoom = String.format("%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format("%.2f", state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);
        }

    };

}
