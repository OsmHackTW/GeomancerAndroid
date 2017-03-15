package tacoball.com.geomancer.view;

import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;

/**
 * 用於 License 頁面，讓文字自動變成超連結
 */
public class LinkView extends AppCompatTextView {

    /**
     * 自動變超連結的設定
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * 建構方法
     */
    public LinkView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

}
