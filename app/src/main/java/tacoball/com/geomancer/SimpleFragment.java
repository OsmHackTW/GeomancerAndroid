package tacoball.com.geomancer;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * 簡易頁面用的 Fragment，目前用於授權和貢獻者頁面
 */
public class SimpleFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle args = this.getArguments();
        if (args != null) {
            int layoutId = args.getInt("LAYOUT_ID");
            return inflater.inflate(layoutId, null);
        }
        return null;
    }

}
