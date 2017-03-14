package tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewParent;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FixedFpsSurfaceView extends SurfaceView {

    private static final String TAG = "FixedFpsSurfaceView";
    private static final int FPS = 24;
    private static final boolean TRACE = true;

    private SurfaceMapView parallelMapView;

    public FixedFpsSurfaceView(Context context) {
        this(context, null);
    }

    public FixedFpsSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.setWillNotDraw(false);
        this.getHolder().addCallback(callback);

        if (TRACE) {
            long thId = Thread.currentThread().getId();
            String msg = String.format(Locale.getDefault(), "Init MySurface at thread #%d", thId);
            Log.d(TAG, msg);
        }
    }

    public SurfaceMapView getParentViewGroup() {
        if (parallelMapView == null) {
            ViewParent parent = getParent();
            if (parent instanceof SurfaceMapView) {
                parallelMapView = (SurfaceMapView)parent;
            }
        }

        return parallelMapView;
    }

    private void onDrawSurface(Canvas androidCanvas) {
        if (getParentViewGroup()!=null) {
            org.mapsforge.core.graphics.Canvas graphicContext = AndroidGraphicFactory.createGraphicContext(androidCanvas);

            parallelMapView.getFrameBuffer().draw(graphicContext);
            parallelMapView.getMapScaleBar().draw(graphicContext);
            parallelMapView.getFpsCounter().draw(graphicContext);

            graphicContext.destroy();
        }

        if (TRACE) {
            long thId = Thread.currentThread().getId();
            String msg = String.format(Locale.getDefault(), "Draw MySurface at thread #%d", thId);
            Log.d(TAG, msg);
        }
    }

    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {

        ScheduledExecutorService exesvc;

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            if (TRACE) {
                Log.d(TAG, "surfaceChanged()");
            }
            try {
                long period = 1000000 / FPS;
                exesvc.scheduleAtFixedRate(drawTask, 0, period, TimeUnit.MICROSECONDS);
                exesvc.schedule(new Runnable() {
                    @Override
                    public void run() {
                        exesvc.shutdown();
                    }
                }, 1000, TimeUnit.SECONDS);
            } catch(RejectedExecutionException ex) {
                Log.e(TAG, "Cannot run drawTask.");
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            if (TRACE) {
                Log.d(TAG, "surfaceCreated()");
            }
            exesvc = Executors.newSingleThreadScheduledExecutor();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            if (TRACE) {
                Log.d(TAG, "surfaceDestroyed()");
            }
            exesvc.shutdown();
            exesvc = null;
        }

    };

    private Runnable drawTask = new Runnable() {

        @Override
        public void run() {
            Canvas canvas = getHolder().lockCanvas();
            if (canvas!=null) {
                onDrawSurface(canvas);
                getHolder().unlockCanvasAndPost(canvas);
                postInvalidate();
            } else {
                Log.w(TAG, "canvas is null.");
            }
        }
    };

}
