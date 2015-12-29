package tacoball.com.geomancer;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.io.IOException;


/**
 *
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    private MapView  mMapView;
    private TextView mTxvZoom;

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
        AndroidGraphicFactory.clearResourceFileCache();
        AndroidGraphicFactory.clearResourceMemoryCache();
        mMapView = (MapView)view.findViewById(R.id.mapView);
        mTxvZoom = (TextView)view.findViewById(R.id.zoomValue);

        try {
            initMapView();
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        // Inflate the layout for this fragment
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mMapView.destroyAll();
    }

    private void initMapView() throws IOException {
        final String cacheName = "mymapcache";
        final String mapName   = "taiwan-201512.map";
        final byte   minZoom   = 7;
        final byte   maxZoom   = 16;

        LatLong initLoc   = new LatLong(23.517, 121.000);
        byte    initZoom  = minZoom;
        //LatLong initLoc   = new LatLong(25.076, 121.544);
        //byte    initZoom  = 14;

        File mapFile = new File(Environment.getExternalStorageDirectory(), mapName);
        MapDataStore mapDataStore = new MapFile(mapFile);

        // create RenderTheme
        AssetsRenderTheme theme = new AssetsRenderTheme(getActivity(), "", "TacoRenderTheme.xml");

        // create TileCache
        TileCache tileCache = AndroidUtil.createTileCache(
            getActivity(),
            cacheName,
            mMapView.getModel().displayModel.getTileSize(),
            1f,
            mMapView.getModel().frameBufferModel.getOverdrawFactor()
        );

        // create TileRendererLayer, need MapDataStore & TileCache
        TileRendererLayer tileRendererLayer = new TileRendererLayer(
            tileCache,
            mapDataStore,
            mMapView.getModel().mapViewPosition,
            false,
            true,
            AndroidGraphicFactory.INSTANCE
        );
        tileRendererLayer.setXmlRenderTheme(theme);
        //tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        // set UI of mapView
        mMapView.setClickable(true);
        mMapView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateZoomValue();
            }
        });
        mMapView.getMapZoomControls().setZoomLevelMin(minZoom);
        mMapView.getMapZoomControls().setZoomLevelMax(maxZoom);
        mMapView.getMapZoomControls().setAutoHide(true);
        mMapView.getMapZoomControls().show();
        mMapView.getModel().mapViewPosition.setCenter(initLoc);
        mMapView.getModel().mapViewPosition.setZoomLevel(initZoom);
        mMapView.getModel().mapViewPosition.setMapLimit(new BoundingBox(22.0, 120.20, 26.0, 122.50));
        updateZoomValue();

        // check if this is a new compilation
        Log.e(TAG, "Test 1");

        // add Layer to mapView
        mMapView.getLayerManager().getLayers().add(tileRendererLayer);
    }

    private void updateZoomValue() {
        String txt = String.format("%d", mMapView.getModel().mapViewPosition.getZoomLevel());
        mTxvZoom.setText(txt);
    }

}
