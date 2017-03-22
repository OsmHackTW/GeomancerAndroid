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
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.TileLayer;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.MainUtils;

/**
 * 台灣地圖前端
 */
public class TaiwanMapView extends MapView {

    private static final String TAG = "TaiwanMapView";

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

    /**
     * 配置地圖
     */
    public TaiwanMapView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        if (!isInEditMode()) {
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
    }

    /**
     * 配置地圖操作狀態接收器
     *
     * @param listener 操作狀態接收器
     */
    public void setStateChangeListener(StateChangeListener listener) {
        mStateChangeListener = listener;
    }

    /**
     * 設定所在位置圖標
     *
     * @param resId 圖標資源 ID
     */
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

    /**
     * 移動到目前位置
     */
    public boolean gotoMyPosition() {
        boolean gps = mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean net = mLocationMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (gps || net) {
            // 先採用最後一次定位的結果，限制 10 分鐘內的定位資訊
            // TODO: 這部份移到 MainUtils
            Location locGps = mLocationMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location locNet = mLocationMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (locGps != null || locNet != null) {
                Location locLast;
                if (locGps != null && locNet != null) {
                    locLast = (locGps.getTime() > locNet.getTime()) ? locGps : locNet;
                } else {
                    if (locGps == null) {
                        locLast = locNet;
                    } else {
                        locLast = locGps;
                    }
                }
                long timeDiff = System.currentTimeMillis() - locLast.getTime();
                if (timeDiff < 600000) {
                    mLocListener.onLocationChanged(locLast);
                }
            }

            // 再使用精確定位
            mLocationMgr.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocListener, null);
            Log.e(TAG, "GPS 取座標");
            mLocationMgr.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, mLocListener, null);
            Log.e(TAG, "網路取座標");
            return true;
        } else {
            return false;
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

    @Override
    public void destroy() {
        // TODO: Release map resources
        //mMapFile.close();

        // Save state
        mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            .edit()
            .putFloat("cLat", (float)mState.cLat)
            .putFloat("cLng", (float)mState.cLng)
            .putInt("zoom", mState.zoom)
            .apply();

        mSensorMgr.unregisterListener(mAzimuthListener);
        disableGps();
        super.destroy();
    }

    private void disableGps() {
        mLocationMgr.removeUpdates(mLocListener);
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

    /**
     * 圖層載入程式
     */
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

    /**
     * 觸發狀態變更事件
     */
    private void triggerStateChange() {
        if (mStateChangeListener!=null) {
            mStateChangeListener.onStateChanged(mState);
        }
    }

    /**
     * 繪圖動作
     *
     * @param androidCanvas 畫布
     */
    @Override
    protected void onDraw(Canvas androidCanvas) {
        super.onDraw(androidCanvas);

        if (isInEditMode()) {
            // TODO: 顯示預設地圖
        } else {
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
    }

    /**
     * 方位角資訊處理程式
     */
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

    /**
     * 收到位置資訊的處理
     */
    private LocationListener mLocListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            LatLong newLoc = new LatLong(location.getLatitude(), location.getLongitude());
            mState.myLat = newLoc.latitude;
            mState.myLng = newLoc.longitude;
            triggerStateChange();

            String msg = String.format(Locale.getDefault(), "我的位置: (%.2f, %.2f)", mState.myLat, mState.myLng);
            Log.i(TAG, msg);

            // 所在位置顯示圖標
            if (mMyLocationMarker!=null) {
                mMyLocationMarker.setLatLong(newLoc);
            }

            // 移動地圖到所在位置
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
