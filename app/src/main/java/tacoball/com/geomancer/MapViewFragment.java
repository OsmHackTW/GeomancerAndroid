package tacoball.com.geomancer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rotation.RotateView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tacoball.com.geomancer.map.PinGroup;
import tacoball.com.geomancer.map.TaiwanMapView;
import tacoball.com.geomancer.view.CircleButton;

/**
 * 測量風水程式
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    private static final byte ZOOM_LIMIT = 13;

    // 介面元件
    private TextView      mTxvLatitude;    // 緯度文字
    private TextView      mTxvLongitude;   // 經度文字
    private TextView      mTxvZoom;        // 縮放比文字
    private TextView      mTxvAzimuth;     // 方位角文字
    private TextView      mTxvHint;        // 地圖放大提示訊息
    private Button        mBtPosition;     // 定位按鈕
    private Button        mBtMeasure;      // 測量風水按鈕
    private Button        mBtClear;        // 清除按鈕
    private CircleButton  mBtMore;         // 展開/收合按鈕
    private CircleButton  mBtSettings;     // 設定按鈕
    private CircleButton  mBtContributors; // 貢獻者按鈕
    private CircleButton  mBtLicense;      // 授權按鈕
    private ImageView     mImCompass;      // 羅盤

    // 介面元件 (詳細資訊)
    private ViewGroup mVgDetail;
    private TextView  mTxvSummary;
    private TextView  mTxvLink;

    // 地圖元件
    private RotateView    mRotateView;    // 旋轉元件
    private TaiwanMapView mMapView;       // 地圖
    private PinGroup      mUnluckyHouses; // 凶宅地標

    // 資源元件
    private SQLiteDatabase mUnluckyHouseDB; // 凶宅資料庫

    // 設定值
    private boolean isRotateByAzimuth; // 自動旋轉

    /**
     * 準備動作
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        ViewGroup mFragLayout = (ViewGroup)inflater.inflate(R.layout.fragment_main, container, false);

        // 狀態列
        mTxvZoom     = mFragLayout.findViewById(R.id.txvZoomValue);
        mTxvLatitude = mFragLayout.findViewById(R.id.txvLatitude);
        mTxvLongitude = mFragLayout.findViewById(R.id.txvLongitude);
        mTxvAzimuth  = mFragLayout.findViewById(R.id.txvAzimuthValue);

        // 地圖放大提示訊息
        mTxvHint = mFragLayout.findViewById(R.id.txvHint);

        // 指北針
        mImCompass = mFragLayout.findViewById(R.id.imCompass);
        try {
            SVG svg = SVG.getFromAsset(activity.getAssets(), "icons/compass.svg");
            Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            svg.renderToCanvas(canvas);
            mImCompass.setImageBitmap(bitmap);
        } catch(SVGParseException ex) {
            Log.e(TAG, "SVGParseException");
            Log.e(TAG, ex.getMessage());
        } catch(IOException ex) {
            Log.e(TAG, "IOException");
            Log.e(TAG, ex.getMessage());
        }

        // 按鈕列
        mBtPosition = mFragLayout.findViewById(R.id.btPosition);
        mBtMeasure  = mFragLayout.findViewById(R.id.btMeasure);
        mBtClear    = mFragLayout.findViewById(R.id.btClear);
        mBtMore = mFragLayout.findViewById(R.id.btnMore);
        mBtSettings = mFragLayout.findViewById(R.id.btnSettings);
        mBtContributors = mFragLayout.findViewById(R.id.btnContributors);
        mBtLicense = mFragLayout.findViewById(R.id.btnLicense);

        // POI 詳細資訊
        mVgDetail   = mFragLayout.findViewById(R.id.glyPointInfo);
        mTxvSummary = mFragLayout.findViewById(R.id.txvSummaryContent);
        mTxvLink    = mFragLayout.findViewById(R.id.txvURLContent);
        mTxvLink.setMovementMethod(LinkMovementMethod.getInstance());

        // 地圖
        mMapView = mFragLayout.findViewById(R.id.mapView);
        mUnluckyHouses = new PinGroup("凶", AndroidGraphicFactory.INSTANCE, mMapView.getMapViewProjection());
        mMapView.addLayer(mUnluckyHouses);
        mRotateView = mFragLayout.findViewById(R.id.rotateView);

        // 事件配置
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mBtClear.setOnClickListener(mClickListener);
        mBtContributors.setOnClickListener(mClickListener);
        mBtSettings.setOnClickListener(mClickListener);
        mBtMore.setOnClickListener(mClickListener);
        mBtLicense.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);
        mUnluckyHouses.setOnSelectListener(mOnSelectPin);

        // 載入設定值
        reloadSettings();

        // 資料庫配置
        try {
            mUnluckyHouseDB = MainUtils.openReadOnlyDB(activity, MainUtils.UNLUCKY_HOUSE);
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }

        return mFragLayout;
    }

    /**
     * 善後動作
     */
    @Override
    public void onDestroyView() {
        mMapView.destroyAll();

        if (mUnluckyHouseDB != null) {
            mUnluckyHouseDB.close();
        }

        super.onDestroyView();
    }

    public void reloadSettings() {
        // 自動旋轉、地圖風格變更設定
        // getActivity() 為 null 時會爆掉，有檢查才不會發生 NPE
        Context context = getActivity();
        if (context != null) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            isRotateByAzimuth = pref.getBoolean("rotate_by_azimuth", false);
            String msg = String.format("旋轉方位角功能: %s", isRotateByAzimuth);
            Log.d(TAG, msg);

            // 地圖風格設定
            String newTheme = pref.getString("render_theme", "classic");
            mMapView.reloadTheme(newTheme);
            msg = String.format("地圖風格: %s", newTheme);
            Log.d(TAG, msg);
        }
    }

    /**
     * 定位與測量風水按鈕事件處理
     */
    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            // 不管做什麼事都關閉詳細資訊
            hideDetail();

            // 定位
            if (v==mBtPosition) {
                mMapView.gotoMyPosition();
                // if (!mMapView.gotoMyPosition()) {
                    // 無法定位，建議使用者啟用定位功能
                    // Toast.makeText(activity, R.string.prompt_cannot_access_location, Toast.LENGTH_LONG).show();
                // }
            }

            // 展開/收合按鈕
            if (v==mBtMore) {
                Log.d(TAG, "Click More");
                if (mBtSettings.getVisibility()==View.VISIBLE) {
                    mBtSettings.setVisibility(View.INVISIBLE);
                    mBtContributors.setVisibility(View.INVISIBLE);
                    mBtLicense.setVisibility(View.INVISIBLE);
                } else {
                    mBtSettings.setVisibility(View.VISIBLE);
                    mBtContributors.setVisibility(View.VISIBLE);
                    mBtLicense.setVisibility(View.VISIBLE);
                }
            }

            // 切換到設定頁
            if (v==mBtSettings) {
                Log.d(TAG, "Click Settings");
                activity.sendBroadcast(MainUtils.buildFragmentSwitchIntent("SETTINGS"));
            }

            // 切換到貢獻者頁
            if (v==mBtContributors) {
                Log.d(TAG, "Click Contributors");
                activity.sendBroadcast(MainUtils.buildFragmentSwitchIntent("CONTRIBUTORS"));
            }

            // 切換到授權頁
            if (v==mBtLicense) {
                Log.d(TAG, "Click License");
                activity.sendBroadcast(MainUtils.buildFragmentSwitchIntent("LICENSE"));
            }

            // 測量風水
            if (v==mBtMeasure) {
                // 地圖範圍轉字串，供 SQLite 查詢用
                BoundingBox bbox =  mMapView.getBoundingBox();
                String[] bboxString = {
                    Double.toString(bbox.minLatitude),
                    Double.toString(bbox.minLongitude),
                    Double.toString(bbox.maxLatitude),
                    Double.toString(bbox.maxLongitude)
                };

                // 查凶宅
                List<String> summaries = new ArrayList<>();
                mUnluckyHouses.clear();
                searchUnluckyHouse(bboxString);
                if (mUnluckyHouses.size() > 0) {
                    summaries.add(String.format(Locale.getDefault(), "凶宅 x%d", mUnluckyHouses.size()));
                }

                // 顯示摘要
                if (summaries.size() > 0) {
                    String msg = TextUtils.join("、", summaries);
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, R.string.term_peace, Toast.LENGTH_SHORT).show();
                }
            }

            if (v==mBtClear) {
                mUnluckyHouses.clear();
            }
        }

    };

    // 查凶宅
    private void searchUnluckyHouse(String[] bbox) {
        String[] cols = {"id", "approach", "lat", "lng"};
        String   cond = "lat>=? AND lng>=? AND lat<=? AND lng<=?";
        Cursor   cur  = mUnluckyHouseDB.query("unluckyhouse", cols, cond, bbox, "", "", "");

        if (cur.getCount() > 0) {
            while (cur.moveToNext()) {
                String id      = cur.getString(0);
                String pat     = getString(R.string.pattern_unluckyhouse_subject);
                String subject = String.format(Locale.getDefault(), pat, cur.getString(1));
                double lat = cur.getDouble(2);
                double lng = cur.getDouble(3);
                mUnluckyHouses.add(new LatLong(lat, lng), subject, id);
            }
        }
        cur.close();
    }

    // 顯示詳細資訊
    private void showDetail(String summary, String linkURL, String linkText) {
        String linkHtml = String.format(Locale.getDefault(), "<a href=\"%s\">%s</a>", linkURL, linkText);
        mTxvSummary.setText(summary);
        mTxvLink.setText(Html.fromHtml(linkHtml));
        mVgDetail.setVisibility(View.VISIBLE);
    }

    // 隱藏詳細資訊
    private void hideDetail() {
        mVgDetail.setVisibility(View.INVISIBLE);
    }

    /**
     * 同步地圖狀態值 (經緯度、縮放比、方位角)，限制 Z>=15 才允許測量風水
     */
    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        private static final int ANGLE_SCALE = 5;
        private int prevReducedAzimuth = 0;

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLat = String.format(Locale.getDefault(), "%.4f", state.cLat);
            mTxvLatitude.setText(txtLat);
            String txtLng = String.format(Locale.getDefault(), "%.4f", state.cLng);
            mTxvLongitude.setText(txtLng);

            String txtZoom = String.format(Locale.getDefault(), "%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format(Locale.getDefault(), "%d", (int)state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);

            // 圖釘隨方位角旋轉
            if (isRotateByAzimuth) {
                // 換算粗略方位角，假如粗略方位角有變化才旋轉畫面
                int reducedAzimuth = (int)(state.myAzimuth / ANGLE_SCALE) * ANGLE_SCALE;
                if (reducedAzimuth != prevReducedAzimuth) {
                    // String msg = String.format(Locale.getDefault(), "粗略方位角: %d", reducedAzimuth);
                    // Log.e(TAG, msg);
                    mRotateView.setHeading(-reducedAzimuth);
                    mUnluckyHouses.setAngle(-reducedAzimuth);
                    mImCompass.setRotation(reducedAzimuth);
                    prevReducedAzimuth = reducedAzimuth;
                }
            } else {
                if (prevReducedAzimuth != 0) {
                    mRotateView.setHeading(0);
                    mUnluckyHouses.setAngle(0);
                    mImCompass.setRotation(0);
                    prevReducedAzimuth = 0;
                }
            }

            if (state.zoom>=ZOOM_LIMIT) {
                mTxvHint.setVisibility(View.INVISIBLE);
                mBtMeasure.setEnabled(true);
            } else {
                mTxvHint.setVisibility(View.VISIBLE);
                mBtMeasure.setEnabled(false);
            }
        }

    };

    PinGroup.OnSelectListener mOnSelectPin = new PinGroup.OnSelectListener() {

        @Override
        public void OnSelectPin(String category, String id) {
            if (category.equals("凶")) {
                String[] cols = {"address", "news"};
                String[] args = {id};
                Cursor cur = mUnluckyHouseDB.query("unluckyhouse", cols, "id=?", args, "", "", "");
                cur.moveToNext();
                String addr = cur.getString(0);
                // 保留直接取新聞連結設計，以防某天台灣凶宅網倒站
                // String news = cur.getString(1);
                String url  = String.format(Locale.getDefault(), "https://unluckyhouse.com/showthread.php?t=%s", id);
                showDetail(addr, url, "台灣凶宅網");
                cur.close();
            }
        }

    };

}
