package tacoball.com.geomancer;

import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.widget.Toast;

/**
 * 設定畫面
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    // private static final String TAG = "SettingsFragment";

    // POI 項目 (可擴充)
    private final String[] POI_KEYS = {"search_unluckyhouse", "search_unluckylabor"};

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        // 驗證設定
        PreferenceScreen ps = getPreferenceScreen();
        for (String k : POI_KEYS) {
            ps.findPreference(k).setOnPreferenceChangeListener(mPoiValidator);
        }
    }

    // POI 至少要選一項的檢查程式
    Preference.OnPreferenceChangeListener mPoiValidator = new Preference.OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            PreferenceScreen ps = getPreferenceScreen();
            Boolean newChecked = (Boolean)newValue;

            if (newChecked.booleanValue() == false) {
                int checkedCount = 0;

                for (String k : POI_KEYS) {
                    if (preference.getKey().equals(k)) continue;
                    CheckBoxPreference cbp = (CheckBoxPreference)ps.findPreference(k);
                    if (cbp.isChecked()) {
                        checkedCount++;
                    }
                }

                if (checkedCount == 0) {
                    Toast.makeText(getActivity(), R.string.prompt_at_least_one_poi, Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            return true;
        }

    };

}
