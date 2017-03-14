package tacoball.com.geomancer.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.TileLayer;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.android.view.MapView;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.MainUtils;

/**
 * 台灣地圖前端
 */
public class TaiwanMapView extends MapView {

    private static final String TAG = "TacoMapView";

    public static final boolean SEE_DEBUGGING_POINT = false;

    private static final boolean USE_TWO_LEVEL_CACHE = true;

    private Context         mContext;
    private SensorManager   mSensorMgr;
    private LocationManager mLocationMgr;
    private int             mMyLocationImage;
    private Marker          mMyLocationMarker;
    private PointGroup      mPointGroup;
    private ViewGroup       mInfoView;

    private State mState = new State();
    private StateChangeListener mStateChangeListener;

    private boolean mOneTimePositioning = false;
    private boolean mGpsEnabled         = false;

    private long mPrevOnDraw = 10;

    public static class State {
        public double cLat;
        public double cLng;
        public int    zoom;
        public double myLat = -1;
        public double myLng = -1;
        public double myAzimuth;
    }

    public interface StateChangeListener {
        void onStateChanged(State state);
    }

    public TaiwanMapView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        try {
            mContext     = context;
            mSensorMgr   = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mLocationMgr = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

            Sensor rv = mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSensorMgr.registerListener(mAzimuthListener, rv, SensorManager.SENSOR_DELAY_UI);
            initView();
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    public void setStateChangeListener(StateChangeListener listener) {
        mStateChangeListener = listener;
    }

    public void setMyLocationImage(int resId) {
        // API 21
        //Drawable d = mContext.getDrawable(resId);
        Drawable d = mContext.getResources().getDrawable(resId);

        if (d!=null) {
            mMyLocationImage = resId;
            if (mMyLocationMarker!=null) {
                getLayerManager().getLayers().remove(mMyLocationMarker);
            }

            org.mapsforge.core.graphics.Bitmap markerBitmap = AndroidGraphicFactory.convertToBitmap(d);
            mMyLocationMarker = new Marker(new LatLong(0.0f, 0.0f), markerBitmap, 0, 0);
            getLayerManager().getLayers().add(mMyLocationMarker);
        }
    }

    public void gotoMyPosition() {
        mOneTimePositioning = true;
        if (!mGpsEnabled) {
            enableGps();
        }
    }

    public void showPoints(PointInfo[] info) {
        mPointGroup.setPoints(info);
    }

    public void setInfoView(ViewGroup layout, TextView descView, TextView urlView) {
        mInfoView = layout;
        mPointGroup.setInfoContainer(layout);
        mPointGroup.setDescriptionView(descView);
        mPointGroup.setURLView(urlView);
    }

    /*
    public void startTracing() {
        mOneTimePositioning = false;
        if (!mGpsEnabled) {
            enableGps();
        }
    }

    public void cancelTracing() {
        if (mGpsEnabled&&!mOneTimePositioning) {
            disableGps();
        }
    }
    */

    /**
     * 63ms at Taipei Station Zoom=15
     */
    /*
    public List<PointOfInterest> searchPOIs() {
        List<PointOfInterest> poisInScreen = new ArrayList<>();

        byte z = (byte)mState.zoom;
        if (z<15) return poisInScreen;

        BoundingBox bbox = getBoundingBox();

        int minTy = MercatorProjection.latitudeToTileY(bbox.maxLatitude, z);
        int minTx = MercatorProjection.longitudeToTileX(bbox.minLongitude, z);
        int maxTy = MercatorProjection.latitudeToTileY(bbox.minLatitude, z);
        int maxTx = MercatorProjection.longitudeToTileX(bbox.maxLongitude, z);

        // Step 1
        for (int tx=minTx;tx<=maxTx;tx++) {
            for (int ty=minTy;ty<=maxTy;ty++) {
                Tile tile = new Tile(tx, ty, z, 256);
                MapReadResult result = mMapFile.readMapData(tile);
                for (PointOfInterest poi : result.pointOfInterests) {
                    if (bbox.contains(poi.position)) {
                        poisInScreen.add(poi);
                    }
                }
            }
        }

        // Step 2
        // TODO: ...

        return poisInScreen;
    }
    */

    /*
    private static String tagsToString(List<Tag> tags) {
        StringBuffer sbuf = new StringBuffer();

        for (Tag tag : tags) {
            String tagstr = String.format("%s=\"%s\"", tag.key, tag.value);
            sbuf.append(tagstr);
            sbuf.append(" ");
        }

        return sbuf.toString().trim();
    }
    */

    @Override
    public void destroy() {
        Log.w(TAG, "消滅 TaiwanMapView");
        // TODO: Release map resources
        //mMapFile.close();

        // Save state
        mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            .edit()
            .putFloat("cLat", (float)mState.cLat)
            .putFloat("cLng", (float)mState.cLng)
            .putInt("zoom", mState.zoom)
            .commit();

        mSensorMgr.unregisterListener(mAzimuthListener);
        disableGps();
        super.destroy();
    }

    private void enableGps() {
        // 接收方位感應器和 GPS 訊號
        if (mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mGpsEnabled = true;
            mLocationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10.0f, mLocListener);
        }

        /*
        List<String> providers = mLocationMgr.getAllProviders();
        for (String p : providers) {
            mLocationMgr.requestLocationUpdates(p, 1000, 10.0f, mLocListener);
        }
        */
    }

    private void disableGps() {
        mLocationMgr.removeUpdates(mLocListener);
        mGpsEnabled = false;
    }

    private void initView() throws IOException {
        final byte MIN_ZOOM = 7;
        final byte MAX_ZOOM = 17;

        AndroidGraphicFactory.clearResourceFileCache();
        AndroidGraphicFactory.clearResourceMemoryCache();

        if (SEE_DEBUGGING_POINT) {
            // 檢查點 121.4407269 25.0179735
            mState.cLat = 25.0565;
            mState.cLng = 121.5317;
            mState.zoom = 11;
        } else {
            // Load state or initial state
            SharedPreferences pref = mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE);
            mState.cLat = pref.getFloat("cLat", 25.0744f);
            mState.cLng = pref.getFloat("cLng", 121.5391f);
            mState.zoom = pref.getInt("zoom", 15);
        }

        // 這個地圖只是用來觀察 BBox
        MapDataStore map = MainUtils.openMapData(mContext);
        BoundingBox bbox = map.boundingBox();
        map.close();

        // add Layer to mapView
        getLayerManager().getLayers().add(loadThemeLayer("Taiwan", false));

        // set UI of mapView
        setClickable(true);
        setCenter(new LatLong(mState.cLat, mState.cLng));
        setZoomLevel((byte) mState.zoom);
        setZoomLevelMin(MIN_ZOOM);
        setZoomLevelMax(MAX_ZOOM);
        //getMapZoomControls().setAutoHide(true);
        //getFpsCounter().setVisible(true);
        getMapZoomControls().show();
        getModel().mapViewPosition.setMapLimit(bbox);

        // Build pin_unluckyhouse points
        mPointGroup = new PointGroup(getContext(), getLayerManager().getLayers(), 1000);
    }

    private TileLayer loadThemeLayer(String themeName, boolean isTransparent) throws IOException {
        String themeFileName  = String.format("%sTheme.xml", themeName);
        final String cacheName = String.format("%sCache", themeName);

        final TileCache cache;
        final int cacheSize = 64; // 64 x 256 x 256 x 4 (about 16MB)

        if (USE_TWO_LEVEL_CACHE) {
            // The second level is not SD card but an emulated one.
            cache = AndroidUtil.createExternalStorageTileCache(
                mContext,
                cacheName,
                cacheSize,
                getModel().displayModel.getTileSize(),
                false
            );
        } else {
            // Get best cache dir, really get SD card not emulated.
            File bestDir = null;
            File[] dirs = mContext.getExternalCacheDirs();
            for (File dir : dirs) {
                if (dir != null) {
                    bestDir = dir;
                }
            }
            File cacheDir = new File(bestDir, cacheName);
            cache = new FileSystemTileCache(500, cacheDir, AndroidGraphicFactory.INSTANCE, true);
        }

        return AndroidUtil.createTileRendererLayer(
            cache,
            getModel().mapViewPosition,
            MainUtils.openMapData(mContext),
            new AssetsRenderTheme(mContext, "themes/", themeFileName),
            isTransparent,
            true,
            true
        );
    }

    private void triggerStateChange() {
        if (mStateChangeListener!=null) {
            mStateChangeListener.onStateChanged(mState);
        }
    }

    @Override
    protected void onDraw(Canvas androidCanvas) {
        super.onDraw(androidCanvas);

        // control rate of triggerStateChange()
        long fps = 50;
        long currOnDraw = System.currentTimeMillis();
        if (currOnDraw-mPrevOnDraw>=1000/fps) {
            double lat  = getModel().mapViewPosition.getCenter().latitude;
            double lng  = getModel().mapViewPosition.getCenter().longitude;
            byte   zoom = getModel().mapViewPosition.getZoomLevel();

            if (lat!=mState.cLat || lng!=mState.cLng || zoom!=mState.zoom) {
                mState.cLat = lat;
                mState.cLng = lng;
                mState.zoom = zoom;
                mPrevOnDraw = currOnDraw;
                mInfoView.setVisibility(View.INVISIBLE);
                triggerStateChange();
            }
        }
    }

    private SensorEventListener mAzimuthListener = new SensorEventListener() {

        private static final double AZIMUTH_THRESHOLD = 3.0;
        private static final long   MILLIS_THRESHOLD  = 5000;

        long   prevMillis  = 0;
        double prevAzimuth = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR) {
                long    currMillis  = System.currentTimeMillis();
                double  currAzimuth = Math.toDegrees(Math.asin(event.values[2])*2);
                boolean overAzimuthThreshold = Math.abs(currAzimuth-prevAzimuth)>=AZIMUTH_THRESHOLD;
                boolean overMillisThreshold  = (currMillis-prevMillis)>=MILLIS_THRESHOLD;

                if (overAzimuthThreshold || overMillisThreshold) {
                    prevMillis  = currMillis;
                    prevAzimuth = currAzimuth;
                    mState.myAzimuth = currAzimuth;

                    if (mMyLocationMarker!=null) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate((int)-mState.myAzimuth);

                        Bitmap src = BitmapFactory.decodeResource(mContext.getResources(), mMyLocationImage);
                        Bitmap dst = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
                        Drawable d = new BitmapDrawable(mContext.getResources(), dst);

                        org.mapsforge.core.graphics.Bitmap markerBitmap = AndroidGraphicFactory.convertToBitmap(d);
                        mMyLocationMarker.setBitmap(markerBitmap);
                        mMyLocationMarker.requestRedraw();
                    }

                    triggerStateChange();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { /* unused */ }

    };

    private LocationListener mLocListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            LatLong newLoc = new LatLong(location.getLatitude(), location.getLongitude());
            mState.myLat = newLoc.latitude;
            mState.myLng = newLoc.longitude;
            triggerStateChange();

            String msg = String.format(Locale.getDefault(), "我的位置: (%.2f, %.2f)", mState.myLat, mState.myLng);
            Log.i(TAG, msg);

            if (mMyLocationMarker!=null) {
                mMyLocationMarker.setLatLong(newLoc);
            }

            if (mOneTimePositioning) {
                disableGps();
            }

            getModel().mapViewPosition.setCenter(newLoc);
            getModel().mapViewPosition.setZoomLevel((byte)16);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            String msg = String.format(Locale.getDefault(), "%s: status=%d", provider, status);
            Log.e(TAG, msg);
        }

        @Override
        public void onProviderEnabled(String provider) { /* unused */ }

        @Override
        public void onProviderDisabled(String provider) { /* unused */ }

    };

}
