package tacoball.com.geomancer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.checkupdate.ConfirmUpdateService;

/**
 * 共用程式
 */
public class MainUtils {

    private static final String TAG = "MainUtils";

    // 更新通知的 ID
    private static final int NFID_UPDATE = 233;

    // 各偏好設定 KEY 值
    private static final String PREFKEY_UPDATE_REQUEST   = "UPDATE_REQUEST";     // 使用者要求更新
    private static final String PREFKEY_UPDATE_BY_MOFILE = "UPDATE_FROM_MOFILE"; // 允許行動網路更新

    // 地圖檔名
    public static final String MAP_NAME = "taiwan-taco.map";

    // 更新伺服器
    public static final String UPDATE_SITE = "http://tacosync.com/geomancer"; // Web
    //public static final String UPDATE_SITE = "http://192.168.1.81/geomancer"; // Wifi LAN
    //public static final String UPDATE_SITE = "http://192.168.42.180/geomancer"; // USB LAN

    // 需要檢查更新的檔案清單
    public static final String[] REQUIRED_FILES = {
        MAP_NAME,
        "unluckyhouse.sqlite",
        "unluckylabor.sqlite"
    };

    /**
     * 取得檔案的遠端位置
     */
    public static String getRemoteURL(int fileIndex) {
        return String.format(Locale.getDefault(), "%s/%s.gz", UPDATE_SITE, REQUIRED_FILES[fileIndex]);
    }

    /**
     * 取得檔案的本地存檔路徑
     */
    public static File getSavePath(Context context, int fileIndex) throws IOException {
        if (context==null) {
            throw new IOException("Context is null");
        }

        if (fileIndex>=REQUIRED_FILES.length) {
            throw new IOException("fileIndex out of range");
        }

        String filename = REQUIRED_FILES[fileIndex];
        int begin = filename.lastIndexOf('.') + 1;
        String ext = filename.substring(begin);
        String category = ext;
        if (ext.equals("sqlite")) category = "db";

        File[] dirs = context.getExternalFilesDirs(category);
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) return dirs[i];
        }

        throw new IOException("Cannot get save path");
    }

    /**
     * 取得檔案的本地完整路徑
     */
    public static File getFilePath(Context context, int fileIndex) throws IOException {
        return new File(getSavePath(context, fileIndex), REQUIRED_FILES[fileIndex]);
    }

    /**
     * 例外訊息改進程式，避免捕捉例外時還發生例外
     */
    public static String getReason(final Exception ex) {
        String msg = ex.getMessage();

        if (msg==null) {
            StackTraceElement ste = ex.getStackTrace()[0];
            msg = String.format(
                Locale.getDefault(),
                "%s with null message (%s.%s() Line:%d)",
                ex.getClass().getSimpleName(),
                ste.getClassName(),
                ste.getMethodName(),
                ste.getLineNumber()
            );
        }

        return msg;
    }

    /**
     * 移除資料更新的系統通知
     */
    public static void clearUpdateNotification(Context context) {
        NotificationManager notiMgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notiMgr.cancel(TAG, NFID_UPDATE);
    }

    /**
     * 產生資料更新的系統通知
     */
    public static void buildUpdateNotification(Context context, double mblen) {
        Intent itYes = new Intent(context, ConfirmUpdateService.class).setAction("Yes");
        Intent itNo  = new Intent(context, ConfirmUpdateService.class).setAction("No");
        PendingIntent piYes = PendingIntent.getService(context, 0, itYes, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent piNo  = PendingIntent.getService(context, 0, itNo, PendingIntent.FLAG_UPDATE_CURRENT);
        long[] vpat = {0, 100, 100, 100};

        BitmapDrawable sic = (BitmapDrawable)context.getResources().getDrawable(R.mipmap.geomancer);
        // TODO: Don't know how to kill LINT message.
        Bitmap lic = sic.getBitmap();

        String pat = context.getString(R.string.pattern_confirm_update);
        String msg = String.format(Locale.getDefault(), pat, mblen);

        // TODO: Optimize parameters
        NotificationCompat.Style style = new NotificationCompat.BigTextStyle().bigText(msg);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Notification nf = builder
            .setContentTitle(context.getString(R.string.term_update))
            .setStyle(style)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(lic)
            .setVibrate(vpat)
            .addAction(android.R.drawable.arrow_down_float, context.getString(R.string.term_yes), piYes)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.term_no), piNo)
            .setAutoCancel(false)
            .build();

        NotificationManager notiMgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notiMgr.notify(TAG, NFID_UPDATE, nf);
    }

    /**
     * 紀錄使用者更新要求
     */
    public static void setUpdateRequest(Context context) {
        SharedPreferences.Editor pedit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        pedit.putBoolean(PREFKEY_UPDATE_REQUEST, true).apply();
    }

    /**
     * 清除使用者更新要求
     */
    public static void clearUpdateRequest(Context context) {
        SharedPreferences.Editor pedit = PreferenceManager.getDefaultSharedPreferences(context).edit();
        pedit.putBoolean(PREFKEY_UPDATE_REQUEST, false).apply();
    }

    /**
     * 確認使用者是否要求更新
     */
    public static boolean hasUpdateRequest(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFKEY_UPDATE_REQUEST, false);
    }

    /**
     * 是否允許透過行動網路更新
     */
    public static boolean canUpdateByMobile(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFKEY_UPDATE_BY_MOFILE, true);
    }

}
