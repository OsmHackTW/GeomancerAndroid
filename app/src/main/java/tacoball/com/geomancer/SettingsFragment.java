package tacoball.com.geomancer;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

/**
 * 設定畫面
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
    }

}
