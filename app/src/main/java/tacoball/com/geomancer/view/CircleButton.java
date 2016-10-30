package tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

public class CircleButton extends AppCompatImageView {

    public CircleButton(Context context) {
        super(context);
    }

    public CircleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CircleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Canvas tempCv = new Canvas();
        Drawable d = getDrawable();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        float sw = (float)w/dw;
        float sh = (float)h/dh;
        float s  = Math.max(sw, sh);
        float strokeWidth = 6.0f;

        // Create circle mask
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        tempCv.setBitmap(mask);
        p.setColor(0xffffffff);
        tempCv.drawCircle(w/2, h/2, w/2-1, p);

        // Paste Image
        Bitmap result = Bitmap.createBitmap(w, h , Bitmap.Config.ARGB_8888);
        tempCv.setBitmap(result);
        tempCv.scale(s, s);
        d.draw(tempCv);
        tempCv.setMatrix(new Matrix());

        // Apply circle mask
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        tempCv.drawBitmap(mask, 0, 0, p);
        p.setXfermode(null);

        // Draw circle border
        p.setColor(0xff7b5d1d);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeWidth);
        tempCv.drawCircle(w/2, h/2, w/2-(float)Math.ceil(strokeWidth/2), p);

        // Display
        canvas.drawBitmap(result, 0, 0, p);
    }

}
