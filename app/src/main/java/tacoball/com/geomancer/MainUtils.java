package tacoball.com.geomancer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import tacoball.com.geomancer.tacoball.com.geomancer.view.CancelUpdateService;

/**
 *
 */
public class MainUtils {

    private static final String TAG = "MainUtils";
    private static final int    UPDATE_NFID = 233;
    private static final String PK_WANNA_UPDATE = "map.wanna_update";

    public static final String UPDATE_SITE = "http://tacosync.com/geomancer"; // Web
    //public static final String UPDATE_SITE = "http://192.168.1.104/geomancer"; // Wifi LAN
    //public static final String UPDATE_SITE = "http://192.168.42.180/geomancer"; // USB LAN

    public static final String MAP_NAME = "taiwan-taco.map";

    public static final String[] REQUIRED_FILES = {
        MAP_NAME,
        "unluckyhouse.sqlite",
        "unluckylabor.sqlite"
    };

    public static String getRemoteURL(int fileIndex) {
        return String.format(Locale.getDefault(), "%s/%s.gz", UPDATE_SITE, REQUIRED_FILES[fileIndex]);
    }

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

    public static File getFilePath(Context context, int fileIndex) throws IOException {
        return new File(getSavePath(context, fileIndex), REQUIRED_FILES[fileIndex]);
    }

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
     * Clear cotification, called by MapUpdaterFragment & CancelUpdateService
     *
     * @param context source context
     */
    public static void clearUpdateNotification(Context context) {
        NotificationManager notiMgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notiMgr.cancel(TAG, UPDATE_NFID);

        SharedPreferences.Editor pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
        pref.putBoolean(PK_WANNA_UPDATE, false).apply();
    }

    // Only called by checkMapVersion()
    private static void buildUpdateNotification(Context context) {
        Intent itYes = new Intent(context, MainActivity.class);
        Intent itNo  = new Intent(context, CancelUpdateService.class);
        PendingIntent piYes = PendingIntent.getActivity(context, 0, itYes, 0);
        PendingIntent piNo  = PendingIntent.getService(context, 0, itNo, 0);
        long[] vpat = {0, 100, 100, 100};

        SharedPreferences.Editor pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
        pref.putBoolean(PK_WANNA_UPDATE, true).apply();

        BitmapDrawable sic = (BitmapDrawable)context.getResources().getDrawable(R.mipmap.geomancer);
        Bitmap lic = sic.getBitmap();

        Notification.Builder builder = new Notification.Builder(context);
        Notification nf = builder
                .setContentTitle("地圖更新")
                .setContentText("想下載新版地圖嗎？")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setLargeIcon(lic)
                .setVibrate(vpat)
                .addAction(android.R.drawable.arrow_down_float, "好喔", piYes)
                .addAction(android.R.drawable.ic_delete, "鼻要", piNo)
                .setDeleteIntent(piNo)
                .setAutoCancel(false)
                .build();

        NotificationManager notiMgr = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notiMgr.notify(TAG, UPDATE_NFID, nf);
    }

}
