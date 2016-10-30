package tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import org.mapsforge.core.graphics.Align;
import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.layer.overlay.Marker;

/**
 * POI 圖標物件
 */
public class PointMarker extends Marker {

    //private static final String TAG = "PointMarker";

    private Context    mContext;
    private PointGroup mPointGroup;

    private String   mSubject;
    private Drawable mPinNormal;
    private Drawable mPinFocused;

    private boolean  mFocused = false;

    // 配置空白圖標
    public PointMarker(Context context, PointGroup pointGroup) {
        super(new LatLong(25.0816, 121.5426), null, 0, 0);
        mContext = context;
        mPointGroup = pointGroup;
        setVisible(false);
    }

    // 更新圖標資訊，讓圖標能重複利用
    public void update(PointInfo info) {
        mPinNormal  = info.getNormalDrawable(mContext);
        mPinFocused = info.getFocusedDrawable(mContext);
        mSubject    = info.getSubject();
        setLatLong(info.getLatLong());
        setNormalPin();
    }

    // 設定為一般狀態
    public void setNormalPin() {
        setBitmap(AndroidGraphicFactory.convertToBitmap(mPinNormal));
        setVerticalOffset(-mPinNormal.getIntrinsicHeight()/2);
        mFocused = false;
    }

    // 設定為選取狀態
    public void setFocusedPin() {
        if (mPinFocused==null) {
            return;
        }
        setBitmap(AndroidGraphicFactory.convertToBitmap(mPinFocused));
        setVerticalOffset(-mPinFocused.getIntrinsicHeight() / 2);
        mFocused = true;
    }

    // 點擊事件，如果點擊動作在圖標範圍內就呈現選取狀態
    @Override
    public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
        if (isVisible()) {
            int rx = mPinNormal.getIntrinsicWidth()/2;
            int ry = mPinNormal.getIntrinsicHeight()/2;
            double pinCx = layerXY.x;
            double pinCy = layerXY.y - ry;
            double dx = Math.abs(tapXY.x - pinCx);
            double dy = Math.abs(tapXY.y - pinCy);

            if (dx<rx && dy<ry) {
                mPointGroup.setFocus(this);
                return true;
            }
        }

        return false;
    }

    // 善後動作
    @Override
    protected void onRemove() {
        setBitmap(null);
    }

    // 重畫圖標
    @Override
    public synchronized void draw(BoundingBox boundingBox, byte zoomLevel, Canvas canvas, Point topLeftPoint) {
        // super.draw(boundingBox, zoomLevel, canvas, topLeftPoint);

        long mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.getTileSize());
        double lng = this.getLatLong().longitude;
        double lat = this.getLatLong().latitude;

        Bitmap b = getBitmap();
        double px = MercatorProjection.longitudeToPixelX(lng, mapSize) - topLeftPoint.x - b.getWidth()/2;
        double py = MercatorProjection.latitudeToPixelY(lat, mapSize) - topLeftPoint.y - b.getHeight();
        canvas.drawBitmap(b, (int)px, (int)py);

        if (mFocused) {
            Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
            paint.setStyle(Style.FILL);
            paint.setColor(Color.BLACK);
            paint.setTextSize(40);
            paint.setTextAlign(Align.CENTER);

            double cx  = MercatorProjection.longitudeToPixelX(lng, mapSize) - topLeftPoint.x;
            double cy  = MercatorProjection.latitudeToPixelY(lat, mapSize) - topLeftPoint.y + 30;
            canvas.drawText(mSubject, (int) cx, (int) cy, paint);
        }
    }

}
