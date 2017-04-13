package tacoball.com.geomancer;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rotation.RotateView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
    private TextView      mTxvLocation;    // 經緯度文字
    private TextView      mTxvZoom;        // 縮放比文字
    private TextView      mTxvAzimuth;     // 方位角文字
    private TextView      mTxvHint;        // 地圖放大提示訊息
    private Button        mBtPosition;     // 定位按鈕
    private Button        mBtMeasure;      // 測量風水按鈕
    private CircleButton  mBtMore;         // 展開/收合按鈕
    private CircleButton  mBtSettings;     // 設定按鈕
    private CircleButton  mBtContributors; // 貢獻者按鈕
    private CircleButton  mBtLicense;      // 授權按鈕

    // 介面元件 (詳細資訊)
    private ViewGroup mVgDetail;
    private TextView  mTxvSummary;
    private TextView  mTxvLink;

    // 地圖元件
    private RotateView    mRotateView;    // 旋轉元件
    private TaiwanMapView mMapView;       // 地圖
    private PinGroup      mUnluckyHouses; // 凶宅地標
    private PinGroup      mUnluckyLabors; // 屎缺地標

    // 資源元件
    private SQLiteDatabase mUnluckyHouseDB;
    private SQLiteDatabase mUnluckyLaborDB;

    // 設定值
    private boolean isRotateByAzimuth;

    /**
     * 準備動作
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup mFragLayout = (ViewGroup)inflater.inflate(R.layout.fragment_main, container, false);

        // 狀態列
        mTxvZoom     = (TextView)mFragLayout.findViewById(R.id.txvZoomValue);
        mTxvLocation = (TextView)mFragLayout.findViewById(R.id.txvLocation);
        mTxvAzimuth  = (TextView)mFragLayout.findViewById(R.id.txvAzimuthValue);

        // 地圖放大提示訊息
        mTxvHint = (TextView)mFragLayout.findViewById(R.id.txvHint);

        // 按鈕列
        mBtPosition = (Button)mFragLayout.findViewById(R.id.btPosition);
        mBtMeasure  = (Button)mFragLayout.findViewById(R.id.btMeasure);
        mBtMore = (CircleButton) mFragLayout.findViewById(R.id.btnMore);
        mBtSettings = (CircleButton) mFragLayout.findViewById(R.id.btnSettings);
        mBtContributors = (CircleButton) mFragLayout.findViewById(R.id.btnContributors);
        mBtLicense = (CircleButton)mFragLayout.findViewById(R.id.btnLicense);

        // POI 詳細資訊
        mVgDetail   = (ViewGroup)mFragLayout.findViewById(R.id.glyPointInfo);
        mTxvSummary = (TextView)mFragLayout.findViewById(R.id.txvSummaryContent);
        mTxvLink    = (TextView)mFragLayout.findViewById(R.id.txvURLContent);
        mTxvLink.setMovementMethod(LinkMovementMethod.getInstance());

        // 地圖
        mMapView = (TaiwanMapView)mFragLayout.findViewById(R.id.mapView);
        mUnluckyHouses = new PinGroup("凶", AndroidGraphicFactory.INSTANCE, mMapView.getMapViewProjection());
        mUnluckyLabors = new PinGroup("勞", AndroidGraphicFactory.INSTANCE, mMapView.getMapViewProjection());
        mMapView.addLayer(mUnluckyHouses);
        mMapView.addLayer(mUnluckyLabors);
        mRotateView = (RotateView)mFragLayout.findViewById(R.id.rotateView);

        // 事件配置
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mBtContributors.setOnClickListener(mClickListener);
        mBtSettings.setOnClickListener(mClickListener);
        mBtMore.setOnClickListener(mClickListener);
        mBtLicense.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);
        mUnluckyHouses.setOnSelectListener(mOnSelectPin);
        mUnluckyLabors.setOnSelectListener(mOnSelectPin);

        // 載入設定值
        reloadSettings();

        // 資料庫配置
        try {
            Context ctx = getActivity();
            mUnluckyHouseDB = MainUtils.openReadOnlyDB(ctx, MainUtils.UNLUCKY_HOUSE);
            mUnluckyLaborDB = MainUtils.openReadOnlyDB(ctx, MainUtils.UNLUCKY_LABOR);
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

        if (mUnluckyLaborDB != null) {
            mUnluckyLaborDB.close();
        }

        super.onDestroyView();
    }

    public void reloadSettings() {
        // 自動旋轉設定
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        isRotateByAzimuth = pref.getBoolean("rotate_by_azimuth", false);
        String msg = String.format("旋轉方位角功能: %s", isRotateByAzimuth);
        Log.d(TAG, msg);

        // 地圖風格設定
        // TODO: 這裡改成 TaiwanMapView 檢查風格是否變更可能比較理想
        String newTheme = pref.getString("render_theme", "classic");
        mMapView.reloadTheme(newTheme);
        msg = String.format("地圖風格: %s", newTheme);
        Log.d(TAG, msg);
    }

    /**
     * 定位與測量風水按鈕事件處理
     */
    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            // 不管做什麼事都關閉詳細資訊
            hideDetail();

            // 定位
            if (v==mBtPosition) {
                if (!mMapView.gotoMyPosition()) {
                    // 無法定位，建議使用者啟用定位功能
                    Toast.makeText(getActivity(), R.string.prompt_cannot_access_location, Toast.LENGTH_LONG).show();
                }
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
                getActivity().sendBroadcast(MainUtils.buildFragmentSwitchIntent("SETTINGS"));
            }

            // 切換到貢獻者頁
            if (v==mBtContributors) {
                Log.d(TAG, "Click Contributors");
                getActivity().sendBroadcast(MainUtils.buildFragmentSwitchIntent("CONTRIBUTORS"));
            }

            // 切換到授權頁
            if (v==mBtLicense) {
                Log.d(TAG, "Click License");
                getActivity().sendBroadcast(MainUtils.buildFragmentSwitchIntent("LICENSE"));
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

                SharedPreferences pf = PreferenceManager.getDefaultSharedPreferences(getActivity());
                boolean needUnluckyHouse = pf.getBoolean("search_unlucky_house", false);
                boolean needUnluckyLabor = pf.getBoolean("search_unlucky_labor", false);

                List<String> summaries = new ArrayList<>();

                mUnluckyHouses.clear();
                mUnluckyLabors.clear();

                if (needUnluckyHouse) {
                    searchUnluckyHouse(bboxString);
                    if (mUnluckyHouses.size() > 0) {
                        summaries.add(String.format(Locale.getDefault(), "凶宅 x%d", mUnluckyHouses.size()));
                    }
                }

                if (needUnluckyLabor) {
                    searchUnluckyLabor(bboxString);
                    if (mUnluckyLabors.size() > 0) {
                        summaries.add(String.format(Locale.getDefault(), "屎缺 x%d", mUnluckyLabors.size()));
                    }
                }

                if (summaries.size() > 0) {
                    String msg = TextUtils.join("、", summaries);
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), R.string.term_peace, Toast.LENGTH_SHORT).show();
                }
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

    // 查屎缺
    private void searchUnluckyLabor(String[] bbox) {
        String[] cols = {"id", "corp", "lat", "lng"};
        String   cond = "lat>=? AND lng>=? AND lat<=? AND lng<=?";
        Cursor   cur  = mUnluckyLaborDB.query("unluckylabor", cols, cond, bbox, "", "", "");

        if (cur.getCount()>0) {
            while (cur.moveToNext()) {
                String id   = cur.getString(0);
                String corp = cur.getString(cur.getColumnIndex("corp"));
                double lat  = cur.getDouble(cur.getColumnIndex("lat"));
                double lng  = cur.getDouble(cur.getColumnIndex("lng"));
                String pat  = getString(R.string.pattern_unluckylabor_subject);
                String subject = String.format(Locale.getDefault(), pat, corp);
                mUnluckyLabors.add(new LatLong(lat, lng), subject, id);
            }
        }
        cur.close();
    }

    private void showDetail(String summary, String linkURL, String linkText) {
        String linkHtml = String.format(Locale.getDefault(), "<a href=\"%s\">%s</a>", linkURL, linkText);
        mTxvSummary.setText(summary);
        mTxvLink.setText(Html.fromHtml(linkHtml));
        mVgDetail.setVisibility(View.VISIBLE);
    }

    private void hideDetail() {
        mVgDetail.setVisibility(View.INVISIBLE);
    }

    private String toLawText(String law) {
        StringBuffer buf = new StringBuffer();
        String[] rules = law.split(";");
        for (String rule : rules) {
            if (buf.length() > 0) {
                buf.append('\n');
            }
            buf.append(rule.replaceFirst("(\\d+)", "勞動基準法第$1條").replaceFirst("\\-(\\d+)", "第$1項"));
        }
        return buf.toString();
    }

    private String toGovURL(String gov, String corp) {
        try {
            if (gov.equals("臺北市")) {
                return "http://web2.bola.taipei/bolasearch/chhtml/page/20?q47=" + URLEncoder.encode(corp, "UTF-8");
            }
            if (gov.equals("新北市")) {
                return "http://ilabor.ntpc.gov.tw/cloud/Violate/filter?name1=" + URLEncoder.encode(corp, "UTF-8");
            }
            if (gov.equals("桃園市")) {
                return "http://lhrb.tycg.gov.tw/home.jsp?id=373&parentpath=0%2C14%2C372&mcustomize=onemessages_view.jsp&dataserno=201509090001&aplistdn=ou=data,ou=lhrb4,ou=chlhr,ou=ap_root,o=tycg,c=tw&toolsflag=Y";
            }
            if (gov.equals("台中市")) {
                return "http://www.labor.taichung.gov.tw/ct.asp?xItem=55333&ctNode=23053&mp=117010";
            }
            if (gov.equals("台南市")) {
                return "http://www.tainan.gov.tw/labor/page.asp?nsub=M2A400";
            }
            if (gov.equals("高雄市")) {
                return "http://labor.kcg.gov.tw/IllegalList.aspx?appname=IllegalList";
            }
        } catch(UnsupportedEncodingException ex) {
            // 不會發生
        }

        return "";
    }

    private String toDepartment(String gov) {
        if (gov.equals("臺北市")) {
            return gov + "勞動局";
        } else {
            return gov + "勞工局";
        }
    }

    /**
     * 同步地圖狀態值 (經緯度、縮放比、方位角)，限制 Z>=15 才允許測量風水
     */
    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        private static final int ANGLE_SCALE = 5;
        private int prevReducedAzimuth = 0;

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLoc = String.format(Locale.getDefault(), "(%.4f, %.4f)", state.cLat, state.cLng);
            mTxvLocation.setText(txtLoc);

            String txtZoom = String.format(Locale.getDefault(), "%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format(Locale.getDefault(), "%d", (int)state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);

            // 圖釘隨方位角旋轉
            if (isRotateByAzimuth) {
                // 換算粗略方位角，假如粗略方位角有變化才旋轉畫面
                int reducedAzimuth = (int)(state.myAzimuth / ANGLE_SCALE) * ANGLE_SCALE;
                if (reducedAzimuth != prevReducedAzimuth) {
                    String msg = String.format(Locale.getDefault(), "粗略方位角: %d", reducedAzimuth);
                    Log.e(TAG, msg);
                    mRotateView.setHeading(-reducedAzimuth);
                    mUnluckyHouses.setAngle(-reducedAzimuth);
                    mUnluckyLabors.setAngle(-reducedAzimuth);
                    prevReducedAzimuth = reducedAzimuth;
                }
            } else {
                mRotateView.setHeading(0);
                mUnluckyHouses.setAngle(0);
                mUnluckyLabors.setAngle(0);
                prevReducedAzimuth = 0;
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

            if (category.equals("勞")) {
                String[] cols = {"law", "gov", "corp"};
                String[] args = {id};
                Cursor cur = mUnluckyLaborDB.query("unluckylabor", cols, "id=?", args, "", "", "");
                cur.moveToNext();
                String law  = cur.getString(0);
                String gov  = cur.getString(1);
                String corp = cur.getString(2);
                showDetail(toLawText(law), toGovURL(gov, corp), toDepartment(gov));
                cur.close();
            }
        }

    };

}
