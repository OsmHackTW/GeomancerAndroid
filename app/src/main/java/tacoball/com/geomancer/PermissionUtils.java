package tacoball.com.geomancer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

public class PermissionUtils {

    public static int RC_GOTO_POSITION = 32769;

    /**
     * 請求位置權限
     *
     * @param activity 應用程式
     * @param requestCode 請求代碼，完成請求後接續處理用
     */
    public static void requestLocationPermission(@NonNull final AppCompatActivity activity, final int requestCode) {
        // 沒有位置資訊權限
        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // 權限已拒絕狀態，提示用戶開啟
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivityForResult(intent, requestCode);
                }
            };

            new AlertDialog.Builder(activity)
                .setMessage(R.string.prompt_loc_permission_rejected)
                .setPositiveButton(R.string.prompt_loc_permission_enable, listener)
                .setNegativeButton(R.string.prompt_loc_permission_cancel, null)
                .create()
                .show();
        } else {
            // 權限未設定狀態，請求權限
            activity.requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, requestCode);
        }
    }

}
