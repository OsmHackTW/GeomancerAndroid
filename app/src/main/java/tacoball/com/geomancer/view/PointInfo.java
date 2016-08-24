package tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.mapsforge.core.model.LatLong;

/**
 *
 */
public class PointInfo {

    private double mLat;
    private double mLng;
    private String mSubject;
    private String mDescription = "...";
    private String mURL = "";
    private int mPinNormal;
    private int mPinFocused;

    public PointInfo(double lat, double lng, String subject, int pinNormal, int pinFocused) {
        mLat = lat;
        mLng = lng;
        mSubject    = subject;
        mPinNormal  = pinNormal;
        mPinFocused = pinFocused;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setURL(String url) {
        mURL = url;
    }

    public LatLong getLatLong() {
        return new LatLong(mLat, mLng);
    }

    public Drawable getNormalDrawable(Context context) {
        return context.getResources().getDrawable(mPinNormal);
    }

    public Drawable getFocusedDrawable(Context context) {
        return context.getResources().getDrawable(mPinFocused);
    }

    public String getSubject() {
        return mSubject;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getURL() {
        return mURL;
    }

}
