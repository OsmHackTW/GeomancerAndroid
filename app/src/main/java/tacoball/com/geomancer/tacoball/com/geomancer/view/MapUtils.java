package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import tacoball.com.geomancer.MainActivity;
import tacoball.com.geomancer.R;

/**
 * Utility to maintain map version
 */
public class MapUtils {

    // Map settings
    private static final String TAG         = "MapUtils";
    private static final String MAP_NAME    = "taiwan-taco.map";
    private static final String MAP_URL     = "http://tacosync.com/taiwan-taco.map.gz";
    private static final String MAP_DIR     = "map";
    private static final String USER_AGENT  = "Geomancer 0.0.4";
    private static final int    UPDATE_NFID = 233;

    // Keys of shared preferences
    private static final String PK_ETAG         = "map.etag";
    private static final String PK_EXTRACTED    = "map.extracted";
    private static final String PK_WANNA_UPDATE = "map.wanna_update";

    // Update state
    private static boolean mDownloading = false;
    private static boolean mCancelFlag  = false;

    /**
     * Called by NetworkReceiver
     *
     * @param context NetworkReceiver instance
     */
    public static void checkMapVersion(final Context context) {
        /*
        $ curl -I http://tacosync.com/taiwan-taco.map.gz
        HTTP/1.1 200 OK
        Server: nginx/1.4.6 (Ubuntu)
        Date: Wed, 20 Jan 2016 02:54:17 GMT
        Content-Type: application/octet-stream
        Content-Length: 25400590
        Last-Modified: Tue, 19 Jan 2016 08:43:50 GMT
        Connection: keep-alive
        ETag: "569df746-183950e"
        Accept-Ranges: bytes
        */

        // Cannot run in main thread
        new Thread() {
            @Override
            public void run() {
                HttpUriRequest req = new HttpHead(MAP_URL);
                AndroidHttpClient client = AndroidHttpClient.newInstance(USER_AGENT);

                try {
                    HttpResponse resp = client.execute(req);
                    int status = resp.getStatusLine().getStatusCode();
                    if (status==200) {
                        //long   currLength = Long.parseLong(resp.getLastHeader("Content-Length").getValue());
                        //String currMtime  = resp.getLastHeader("Last-Modified").getValue();
                        String currEtag = resp.getLastHeader("ETag").getValue();

                        /*
                        Log.i(TAG, String.format("Http Status: %d", status));
                        Log.i(TAG, String.format("Content-Length: %d", currLength));
                        Log.i(TAG, String.format("Last-Modified: %s", currMtime));
                        Log.i(TAG, String.format("ETag: %s", currEtag));
                        */

                        SharedPreferences pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
                        String  usedEtag  = pref.getString(PK_ETAG, "");
                        boolean extracted = pref.getBoolean(PK_EXTRACTED, false);
                        if (!usedEtag.equals(currEtag)||!extracted) {
                            String msg;
                            Log.e(TAG, "圖資需要更新");
                            msg = String.format("usedEtag=%s / currEtag=%s", usedEtag, currEtag);
                            Log.e(TAG, msg);
                            msg = String.format("extracted=%s", extracted);
                            Log.e(TAG, msg);

                            buildUpdateNotification(context);
                        } else {
                            Log.i(TAG, "圖資不用更新");
                        }
                    }
                } catch(IOException ex) {
                    Log.e(TAG, ex.getMessage());
                }

                client.close();
            }
        }.start();
    }

    /**
     * Download map
     *
     * @param context  MainActivity
     * @param listener MapUpdaterFragment
     */
    public static void downloadMapFile(final Context context, final MapUpdateListener listener) {
        mCancelFlag = false;

        new Thread() {
            @Override
            public void run() {
                if (mDownloading) return;
                mDownloading = true;

                HttpUriRequest req = new HttpGet(MAP_URL);
                AndroidHttpClient client = AndroidHttpClient.newInstance(USER_AGENT);

                try {
                    HttpResponse resp = client.execute(req);
                    int status = resp.getStatusLine().getStatusCode();
                    if (status==200) {
                        Log.w(TAG, "Download ... 0%");
                        if (listener!=null) listener.onDownload(0);

                        // Save ETag
                        String currEtag = resp.getLastHeader("ETag").getValue();
                        SharedPreferences.Editor pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
                        pref.putString(PK_ETAG, currEtag)
                            .putBoolean(PK_EXTRACTED, false)
                            .apply();

                        // Download
                        long one_percent = (long)Math.ceil(resp.getEntity().getContentLength()/100.0);
                        InputStream  in  = resp.getEntity().getContent();
                        OutputStream out = new FileOutputStream(getCompressedMapFile(context));

                        for (int pg=1;pg<=100;pg++) {
                            if (mCancelFlag) {
                                Log.w(TAG, "Cancel downloading ...");
                                break;
                            }

                            IOUtils.copyLarge(in, out, 0, one_percent);

                            Log.w(TAG, String.format("Download ... %d%%", pg));
                            if (listener!=null) listener.onDownload(pg);
                        }

                        // Close stream
                        out.flush();
                        out.close();
                        in.close();
                    }
                } catch(IOException ex) {
                    Log.e(TAG, ex.getMessage());
                }

                client.close();
                mDownloading = false;
            }
        }.start();
    }

    /**
     * Extract map file
     *
     * @param context  MainActivity
     * @param listener MapUpdaterFragment
     */
    public static void extractMapFile(final Context context, final MapUpdateListener listener) {
        new Thread() {
            @Override
            public void run() {
                try {
                    Log.w(TAG, "Extracting 0% ...");
                    if (listener!=null) listener.onDecompress(0);

                    File compressedMapFile = getCompressedMapFile(context);
                    GZIPInputStream in  = new GZIPInputStream(new FileInputStream(compressedMapFile));
                    OutputStream    out = new FileOutputStream(getMapFile(context));

                    // gzip len = 25400590
                    // map  len = 38204880
                    long size = 38204880; // TODO: get ungzipped size from API
                    long one_percent = (long)Math.ceil(size/100.0);

                    int pg;
                    for (pg=1;pg<=100;pg++) {
                        if (mCancelFlag) {
                            Log.w(TAG, "Cancel extracting ...");
                            break;
                        }

                        IOUtils.copyLarge(in, out, 0, one_percent);

                        Log.w(TAG, String.format("Extracting %d%% ...", pg));
                        if (listener!=null) listener.onDecompress(pg);
                    }
                    // IOUtils.copyLarge(in, out);

                    if (pg>100) {
                        compressedMapFile.delete();
                        SharedPreferences.Editor pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit();
                        pref.putBoolean(PK_EXTRACTED, true).apply();
                    }

                    out.flush();
                    out.close();
                    in.close();
                } catch(IOException ex) {
                    Log.e(TAG, ex.getMessage());
                }
            }
        }.start();
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

    /**
     * Check if need to update map file
     *
     * @param context source context
     * @return true if need to update
     */
    public static boolean needToUpdate(Context context) {
        SharedPreferences pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        boolean wannaUpdate = pref.getBoolean(PK_WANNA_UPDATE, false);
        boolean hasMap = getMapFile(context).exists();
        return (wannaUpdate || !hasMap);
    }

    /**
     * Cancel map update task
     */
    public static void cancelMapUpdate() {
        mCancelFlag  = true;
        mDownloading = false;
    }

    /**
     * Get File instance of map
     *
     * @param context source context
     * @return File instance
     */
    public static File getMapFile(Context context) {
        File dir = getMapDir(context);
        if (dir!=null) {
            return new File(dir, MAP_NAME);
        }
        return null;
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

    // Called by getMapFile() & getCompressedMapFile()
    private static File getMapDir(Context context) {
        if (context==null) {
            return null;
        }
        File[] dirs = context.getExternalFilesDirs(MAP_DIR);
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) return dirs[i];
        }
        return null;
    }

    // Only called by extractMapFile()
    private static File getCompressedMapFile(Context context) {
        File dir = getMapDir(context);
        if (dir!=null) {
            return new File(dir, MAP_NAME+".gz");
        }
        return null;
    }

}
