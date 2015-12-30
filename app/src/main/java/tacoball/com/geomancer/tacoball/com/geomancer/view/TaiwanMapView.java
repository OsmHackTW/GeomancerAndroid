package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;

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
 * Created by raymond on 15/12/30.
 */
public class TaiwanMapView extends MapView {

    private static final String TAG = "TacoMapView";

    private Context  mContext;
    private Runnable mDrawMapListener;

    public TaiwanMapView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        try {
            AndroidGraphicFactory.clearResourceFileCache();
            AndroidGraphicFactory.clearResourceMemoryCache();
            mContext = context;
            initView();
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    public void setDrawMapListener(Runnable listener) {
        mDrawMapListener = listener;
    }

    public void enableGps() {

    }

    public void disableGps() {

    }

    private void initView() throws IOException {
        final String cacheName = "mymapcache";
        final String mapName   = "taiwan-201512.map";
        final byte   minZoom   = 7;
        final byte   maxZoom   = 17;

        //LatLong initLoc   = new LatLong(23.517, 121.000);
        //byte    initZoom  = minZoom;
        LatLong initLoc   = new LatLong(25.076, 121.544);
        byte    initZoom  = 14;

        File mapFile = new File(Environment.getExternalStorageDirectory(), mapName);
        MapDataStore mapDataStore = new MapFile(mapFile);

        // create RenderTheme
        AssetsRenderTheme grounds_theme = new AssetsRenderTheme(mContext, "", "TaiwanGroundsTheme.xml");
        AssetsRenderTheme roads_theme   = new AssetsRenderTheme(mContext, "", "TaiwanRoadsTheme.xml");

        // create TileCache
        TileCache tileCache = AndroidUtil.createTileCache(
                mContext,
                cacheName,
                getModel().displayModel.getTileSize(),
                1f,
                getModel().frameBufferModel.getOverdrawFactor()
        );

        // create TileRendererLayer, need MapDataStore & TileCache
        TileRendererLayer groundsLayer = new TileRendererLayer(
                tileCache,
                mapDataStore,
                getModel().mapViewPosition,
                false,
                true,
                AndroidGraphicFactory.INSTANCE
        );
        groundsLayer.setXmlRenderTheme(grounds_theme);

        // create TileRendererLayer, need MapDataStore & TileCache
        TileRendererLayer roadsLayer = new TileRendererLayer(
                tileCache,
                mapDataStore,
                getModel().mapViewPosition,
                false,
                true,
                AndroidGraphicFactory.INSTANCE
        );
        roadsLayer.setXmlRenderTheme(roads_theme);

        // add Layer to mapView
        getLayerManager().getLayers().add(groundsLayer);
        getLayerManager().getLayers().add(roadsLayer);

        // set UI of mapView
        setClickable(true);
        setCenter(initLoc);
        setZoomLevel(initZoom);
        getMapZoomControls().setZoomLevelMin(minZoom);
        getMapZoomControls().setZoomLevelMax(maxZoom);
        getMapZoomControls().setAutoHide(true);
        getMapZoomControls().show();
        getModel().mapViewPosition.setMapLimit(mapDataStore.boundingBox());
    }

    @Override
    protected void onDraw(Canvas androidCanvas) {
        //Log.e(TAG, "onDraw(...)");
        super.onDraw(androidCanvas);
        if (mDrawMapListener!=null) {
            mDrawMapListener.run();
        }
    }

    /*
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        //Log.e(TAG, "onLayout(...)");
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.e(TAG, "onMeasure(...)");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        //Log.e(TAG, "onSizeChanged(...)");
        super.onSizeChanged(width, height, oldWidth, oldHeight);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //Log.e(TAG, "onTouchEvent(...)");
        return super.onTouchEvent(event);
    }
    */

}
