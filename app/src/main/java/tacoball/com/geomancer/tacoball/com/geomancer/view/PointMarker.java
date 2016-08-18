package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import org.mapsforge.core.graphics.Align;
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
 *
 */
public class PointMarker extends Marker {

    private static final String TAG = "PointMarker";

    private Context    mContext;
    private PointGroup mPointGroup;

    private String   mSubject;
    private Drawable mPinNormal;
    private Drawable mPinFocused;

    private boolean  mFocused = false;

    public PointMarker(Context context, PointGroup pointGroup) {
        super(new LatLong(25.0, 119.5), null, 0, 0);
        setVisible(false, true);

        mContext = context;
        mPointGroup = pointGroup;
    }

    public void update(PointInfo info) {
        mPinNormal  = info.getNormalDrawable(mContext);
        mPinFocused = info.getFocusedDrawable(mContext);
        mSubject    = info.getSubject();
        setLatLong(info.getLatLong());
        setNormalPin();
    }

    public void setNormalPin() {
        setBitmap(AndroidGraphicFactory.convertToBitmap(mPinNormal));
        setVerticalOffset(-mPinNormal.getIntrinsicHeight()/2);
        setVisible(true, true);
        mFocused = false;
    }

    public void setFocusedPin() {
        if (mPinFocused==null) {
            return;
        }
        setBitmap(AndroidGraphicFactory.convertToBitmap(mPinFocused));
        setVerticalOffset(-mPinFocused.getIntrinsicHeight() / 2);
        setVisible(true, true);
        mFocused = true;
    }

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

    @Override
    protected void onRemove() {
        setBitmap(null);
    }

    @Override
    public synchronized void draw(BoundingBox boundingBox, byte zoomLevel, Canvas canvas, Point topLeftPoint) {
        super.draw(boundingBox, zoomLevel, canvas, topLeftPoint);
        if (mFocused) {
            Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
            paint.setStyle(Style.FILL);
            paint.setColor(Color.BLACK);
            paint.setTextSize(40);
            paint.setTextAlign(Align.CENTER);

            long mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.getTileSize());
            double lng = this.getLatLong().longitude;
            double lat = this.getLatLong().latitude;
            double cx = MercatorProjection.longitudeToPixelX(lng, mapSize) - topLeftPoint.x;
            double cy = MercatorProjection.latitudeToPixelY(lat, mapSize) - topLeftPoint.y + 30;
            //canvas.drawText(mSubject, (int) cx, (int) cy, paint);
            canvas.drawText("\u260e", (int) cx, (int) cy, paint);
        }
    }

}
