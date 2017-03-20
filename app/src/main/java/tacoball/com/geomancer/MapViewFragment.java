package tacoball.com.geomancer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.map.android.rotation.RotateView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tacoball.com.geomancer.view.CircleButton;
import tacoball.com.geomancer.view.PointInfo;
import tacoball.com.geomancer.view.TaiwanMapView;

/**
 * 測量風水程式
 */
public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";

    private static final byte ZOOM_LIMIT = 13;

    // 介面元件
    private TaiwanMapView mMapView;        // 地圖
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
    private RotateView    mRotateView;     // 旋轉元件

    // 資源元件
    private SQLiteDatabase mUnluckyHouseDB;
    private SQLiteDatabase mUnluckyLaborDB;

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
        ViewGroup vgInfoContainer   = (ViewGroup)mFragLayout.findViewById(R.id.glyPointInfo);
        TextView  txvSummaryContent = (TextView)mFragLayout.findViewById(R.id.txvSummaryContent);
        TextView  txvURLContent     = (TextView)mFragLayout.findViewById(R.id.txvURLContent);
        txvURLContent.setMovementMethod(LinkMovementMethod.getInstance());

        // 地圖
        mMapView = (TaiwanMapView)mFragLayout.findViewById(R.id.mapView);
        mMapView.setMyLocationImage(R.drawable.arrow_up);
        mMapView.setInfoView(vgInfoContainer, txvSummaryContent, txvURLContent);

        // 旋轉元件
        mRotateView = (RotateView)mFragLayout.findViewById(R.id.rotateView);

        // 事件配置
        mBtPosition.setOnClickListener(mClickListener);
        mBtMeasure.setOnClickListener(mClickListener);
        mBtContributors.setOnClickListener(mClickListener);
        mBtSettings.setOnClickListener(mClickListener);
        mBtMore.setOnClickListener(mClickListener);
        mBtLicense.setOnClickListener(mClickListener);
        mMapView.setStateChangeListener(mMapStateListener);

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
        Log.w(TAG, "消滅 MapViewFragment");
        mMapView.destroyAll();

        if (mUnluckyHouseDB != null) {
            mUnluckyHouseDB.close();
        }

        if (mUnluckyLaborDB != null) {
            mUnluckyLaborDB.close();
        }

        super.onDestroyView();
    }

    /**
     * 定位與測量風水按鈕事件處理
     */
    private View.OnClickListener mClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
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
                // 查詢前準備
                int uhcnt = 0;
                int ulcnt = 0;

                BoundingBox bbox =  mMapView.getBoundingBox();

                String[] params = {
                    Double.toString(bbox.minLatitude),
                    Double.toString(bbox.maxLatitude),
                    Double.toString(bbox.minLongitude),
                    Double.toString(bbox.maxLongitude)
                };

                String sql;
                Cursor cur;

                List<PointInfo> infolist = new ArrayList<>();

                // 查詢凶宅
                sql = "SELECT id,approach,area,address,lat,lng FROM unluckyhouse " +
                      "WHERE state>1 AND lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyHouseDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    while (cur.moveToNext()) {
                        String pat     = getString(R.string.pattern_unluckyhouse_subject);
                        String url     = String.format(Locale.getDefault(), "http://unluckyhouse.com/showthread.php?t=%d", cur.getInt(0));
                        String subject = String.format(Locale.getDefault(), pat, cur.getString(1));
                        String address = cur.getString(3);
                        double lat = cur.getDouble(4);
                        double lng = cur.getDouble(5);
                        PointInfo p = new PointInfo(lat, lng, subject, R.drawable.pin_unluckyhouse, R.drawable.pin_unluckyhouse_bright);
                        p.setDescription(address);
                        p.setDataSource("台灣凶宅網");
                        p.setURL(url);
                        infolist.add(p);

                        uhcnt++;
                    }
                }
                cur.close();

                // 查詢血汗工廠
                sql = "SELECT id,doc_id,corp,law,boss,dt_exe,gov,lat,lng FROM unluckylabor " +
                      "WHERE lat>=? AND lat<=? AND lng>=? AND lng<=?";
                cur = mUnluckyLaborDB.rawQuery(sql, params);
                if (cur.getCount()>0) {
                    while (cur.moveToNext()) {
                        String corp   = cur.getString(cur.getColumnIndex("corp"));
                        String doc_id = cur.getString(cur.getColumnIndex("doc_id"));
                        String law    = cur.getString(cur.getColumnIndex("law"));
                        String boss   = cur.getString(cur.getColumnIndex("boss"));
                        String dt_exe = cur.getString(cur.getColumnIndex("dt_exe"));
                        String gov    = cur.getString(cur.getColumnIndex("gov"));
                        double lat    = cur.getDouble(cur.getColumnIndex("lat"));
                        double lng    = cur.getDouble(cur.getColumnIndex("lng"));

                        StringBuilder law_desc = new StringBuilder();
                        String[] rules = law.split(";");
                        for (String rule : rules) {
                            if (law_desc.length()>0) {
                                law_desc.append('\n');
                            }
                            law_desc.append(rule.replaceFirst("(\\d+)", "勞動基準法第$1條").replaceFirst("\\-(\\d+)", "第$1項"));
                        }

                        String pat     = getString(R.string.pattern_unluckylabor_subject);
                        String subject = String.format(Locale.getDefault(), pat, corp);
                        String detail  = String.format(Locale.getDefault(), "%s (%s)\n%s %s\n違反勞基法項目：%s", doc_id, dt_exe, corp, boss, law_desc.toString());
                        String ds      = "";
                        String url     = "";

                        try {
                            if (gov.equals("臺北市")) {
                                ds  = "臺北市政府勞動局";
                                url = "http://web2.bola.taipei/bolasearch/chhtml/page/20?q47=" + URLEncoder.encode(corp, "UTF-8");
                            }
                            if (gov.equals("新北市")) {
                                ds  = "新北市政府勞工局";
                                url = "http://ilabor.ntpc.gov.tw/cloud/Violate/filter?name1=" + URLEncoder.encode(corp, "UTF-8");
                            }
                            if (gov.equals("桃園市")) {
                                ds  = "桃園市政府勞工局";
                                url = "http://lhrb.tycg.gov.tw/home.jsp?id=373&parentpath=0%2C14%2C372&mcustomize=onemessages_view.jsp&dataserno=201509090001&aplistdn=ou=data,ou=lhrb4,ou=chlhr,ou=ap_root,o=tycg,c=tw&toolsflag=Y";
                            }
                            if (gov.equals("台中市")) {
                                ds  = "台中市政府勞工局";
                                url = "http://www.labor.taichung.gov.tw/ct.asp?xItem=55333&ctNode=23053&mp=117010";
                            }
                            if (gov.equals("台南市")) {
                                ds  = "台南市政府勞工局";
                                url = "http://www.tainan.gov.tw/labor/page.asp?nsub=M2A400";
                            }
                            if (gov.equals("高雄市")) {
                                ds  = "高雄市政府勞工局";
                                url = "http://labor.kcg.gov.tw/IllegalList.aspx?appname=IllegalList";
                            }
                        } catch(UnsupportedEncodingException ex) {
                            // TODO
                        }
                        PointInfo p = new PointInfo(lat, lng, subject, R.drawable.pin_unluckylabor, R.drawable.pin_unluckylabor_bright);
                        p.setDescription(detail);
                        p.setDataSource(ds);
                        p.setURL(url);
                        infolist.add(p);
                        ulcnt++;
                    }
                }
                cur.close();

                // 配置 POI Marker
                if (infolist.size() > 0) {
                    PointInfo[] infoary = new PointInfo[infolist.size()];
                    infolist.toArray(infoary);
                    infolist.clear();

                    mMapView.showPoints(infoary);
                    String pat = getString(R.string.pattern_measure_result);
                    String msg = String.format(Locale.getDefault(), pat, uhcnt, ulcnt);
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), R.string.term_peace, Toast.LENGTH_SHORT).show();
                }
            }
        }

    };

    /**
     * 同步地圖狀態值 (經緯度、縮放比、方位角)，限制 Z>=15 才允許測量風水
     */
    private TaiwanMapView.StateChangeListener mMapStateListener = new TaiwanMapView.StateChangeListener() {

        @Override
        public void onStateChanged(TaiwanMapView.State state) {
            String txtLoc = String.format(Locale.getDefault(), "(%.4f, %.4f)", state.cLat, state.cLng);
            mTxvLocation.setText(txtLoc);

            String txtZoom = String.format(Locale.getDefault(), "%s", state.zoom);
            mTxvZoom.setText(txtZoom);

            String txtAzimuth = String.format(Locale.getDefault(), "%.2f", state.myAzimuth);
            mTxvAzimuth.setText(txtAzimuth);

            // TODO: 最佳化旋轉功能
            // * 啟用/停用
            // * 角度 72 等份防抖動
            mRotateView.setHeading((float)-state.myAzimuth);

            if (state.zoom>=ZOOM_LIMIT) {
                mTxvHint.setVisibility(View.INVISIBLE);
                mBtMeasure.setEnabled(true);
            } else {
                mTxvHint.setVisibility(View.VISIBLE);
                mBtMeasure.setEnabled(false);
            }
        }

    };

}
