package tacoball.com.geomancer.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.mapsforge.core.model.LatLong;

/**
 * POI 資訊
 */
public class PointInfo {

    private double mLat;                 // 緯度
    private double mLng;                 // 經度
    private String mSubject;             // 標題
    private String mDescription = "..."; // 說明
    private String mURL = "";            // 連結
    private int mPinNormal;              // 一般狀態圖示 ID
    private int mPinFocused;             // 選取狀態圖示 ID

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
