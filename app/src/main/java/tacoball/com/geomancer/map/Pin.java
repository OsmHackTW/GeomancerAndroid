package tacoball.com.geomancer.map;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Canvas;
import org.mapsforge.core.graphics.FontFamily;
import org.mapsforge.core.graphics.FontStyle;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.Matrix;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Path;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.layer.Layer;

/**
 * Pin of POI
 * 
 * This Layer draw a pin and some text on the map directly.
 * The pin has selected and unselected state, using two different colors to represent.
 * Under selected state, a label string appears below the pin to explain what it is. 
 * 
 * @author 小璋丸 <virus.warnning@gmail.com>
 */
public class Pin extends Layer {
	
	private static final int PIN_WIDTH = 100;
	private static final int PIN_HEIGHT = 200;
	
    private LatLong latLong;
    private String category;
    private String label;
    private GraphicFactory gf;
    private boolean selected = false;
    private float angle = 0f;
    private float scale = 0.4f;
    
    private int darkColor = 0xff900000;
    private int brightColor = 0xffff0000;

    /**
     * Create a pin without category or label.
     * 
     * @param latLong position
     * @param gf GraphicFactory
     */
    public Pin(LatLong latLong, GraphicFactory gf) {
        this(latLong, "", "", gf);
    }

    /** 
     * Create a pin with category and label.
     * 
     * @param latLong position
     * @param category text in the pin
     * @param label text below the pin
     * @param gf GraphicFactory
     */
    public Pin(LatLong latLong, String category, String label, GraphicFactory gf) {
        super();

        this.latLong = latLong;
        this.category = category;
        this.label = label;
        this.gf = gf;
        
        if (gf.getClass().getSimpleName().equals("AndroidGraphicFactory")) {
        	scale = 1.0f;
        }
    }

    @Override
    public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
    	double dx1 = tapXY.x - layerXY.x;
    	double dy1 = tapXY.y - layerXY.y;
    	
    	double rad = Math.toRadians(-angle);
    	double dx = (dx1 * Math.cos(rad) - dy1 * Math.sin(rad)) / scale;
    	double dy = (dx1 * Math.sin(rad) + dy1 * Math.cos(rad)) / scale;

    	if (Math.abs(dx) < PIN_WIDTH/2 && Math.abs(dy + PIN_HEIGHT/2) < PIN_HEIGHT/2) {
    		selected = !selected;
    		requestRedraw();
    		return true;
    	}
    	
        return false;
    }

	@Override
    public synchronized void draw(BoundingBox boundingBox, byte zoomLevel, Canvas canvas, Point topLeftPoint) {
        long mapSize = MercatorProjection.getMapSize(zoomLevel, displayModel.getTileSize());
        int tx = (int)(MercatorProjection.longitudeToPixelX(latLong.longitude, mapSize) - topLeftPoint.x);
        int ty = (int)(MercatorProjection.latitudeToPixelY(latLong.latitude, mapSize) - topLeftPoint.y);
        
        Paint paint = gf.createPaint();
        if (selected) {
        	paint.setColor(brightColor);	
        } else {
        	paint.setColor(darkColor);
        }

        Bitmap tempBitmap = gf.createBitmap(PIN_WIDTH, PIN_HEIGHT);
        Canvas tempCanvas = gf.createCanvas();
        tempCanvas.setBitmap(tempBitmap);
        
        // Draw triangle
        Path path = gf.createPath();
        path.moveTo(PIN_WIDTH/2, PIN_HEIGHT-1);
        path.lineTo((int)(PIN_WIDTH*0.2), PIN_WIDTH/2);
        path.lineTo((int)(PIN_WIDTH*0.8), PIN_WIDTH/2);
        path.close();
        tempCanvas.drawPath(path, paint);
        
        // Draw circle
        tempCanvas.drawCircle(PIN_WIDTH/2, PIN_WIDTH/2, PIN_WIDTH/2, paint);
        
        // Draw category
        paint.setColor(0xffffffff);
        paint.setTextSize(PIN_WIDTH * 0.55f);
        paint.setTypeface(FontFamily.SANS_SERIF, FontStyle.BOLD);
        int cx = (PIN_WIDTH - paint.getTextWidth(category))/2;
        int cy = (int)(PIN_WIDTH/2 + paint.getTextHeight(category)*0.35);
        tempCanvas.drawText(category, cx, cy, paint);

        // Paste pin
        Matrix matrix = gf.createMatrix();
        matrix.translate(tx, ty);
        matrix.scale(scale, scale);
        matrix.rotate((float)Math.toRadians(angle));
        matrix.translate(-PIN_WIDTH/2, -PIN_HEIGHT);
        canvas.drawBitmap(tempBitmap, matrix);

        // Draw label
        if (selected) {
            paint.setColor(0xff000000);
            paint.setTextSize(PIN_WIDTH * 0.4f);
            int margin = 10;
            int lw = paint.getTextWidth(label);
            int lh = paint.getTextHeight(label);
            int fw = lw + margin * 2;
            int fh = lh + margin * 2;
            tempBitmap = gf.createBitmap(fw, fh);
            tempCanvas.setBitmap(tempBitmap);
            tempCanvas.fillColor(0xe0ffd070);
            tempCanvas.drawText(label, margin, lh + margin - 3, paint);

            matrix.reset();
            matrix.translate(tx, ty);
            matrix.scale(scale, scale);
            matrix.rotate((float)Math.toRadians(angle));
            matrix.translate(-fw/2, margin);

            canvas.drawBitmap(tempBitmap, matrix);
        }
    }

	/**
     * Return label of the pin.
     * 
     * @return label
     */
    public synchronized String getLabel() {
    	return label;
    } 
    
    @Override
    public synchronized LatLong getPosition() {
        return this.latLong;
    }
    
    /**
     * Set colors of the pin.  
     * 
     * @param darkColor unselected state color
     * @param brightColor selected state color
     */
    public synchronized void setPinColors(int darkColor, int brightColor) {
    	this.darkColor = darkColor;
    	this.brightColor = brightColor;
    }
    
    /**
     * Set position of the pin.
     * 
     * @param latLong position
     */
    public synchronized void setPosition(LatLong latLong) {
        this.latLong = latLong;
    }
    
    /**
     * Set selection state of the pin.
     * 
     * @param selected
     */
    public synchronized void setSelected(boolean selected) {
    	this.selected = selected;
    }
    
    /**
     * Set angle of pin.
     * 
     * @param angle
     */
    public synchronized void setAngle(float angle) {
    	this.angle = angle;
    }
    
}

