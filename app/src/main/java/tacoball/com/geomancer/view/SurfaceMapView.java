package tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.ViewGroup;

import org.mapsforge.map.android.view.MapView;

/**
 * Android MapView using SurfaceView
 */
public class SurfaceMapView extends MapView {

    private FixedFpsSurfaceView mapSurface;

    public SurfaceMapView(Context context) {
        this(context, null);
    }

    public SurfaceMapView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Create SurfaceView to draw tiles.
        mapSurface = new FixedFpsSurfaceView(context);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        addViewInLayout(mapSurface, -1, params, true);

        // Move MapZoomControls to top of MapView.
        removeViewInLayout(getMapZoomControls());
        params = getMapZoomControls().getLayoutParams();
        addViewInLayout(getMapZoomControls(), -1, params, true);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int w = mapSurface.getMeasuredWidth();
        int h = mapSurface.getMeasuredHeight();
        mapSurface.layout(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas androidCanvas) {
        // Do nothing, draw everything by SurfaceView.
    }

    @Override
    public void destroy() {
        this.removeViewInLayout(mapSurface);
        // mapSurface.release();
        mapSurface = null;
        super.destroy();
    }

}
