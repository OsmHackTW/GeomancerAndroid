package tacoball.com.geomancer.view;

import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;

/**
 *
 */
public class LinkView extends AppCompatTextView {

    /**
     *
     * @param context
     */
    public LinkView(Context context) {
        super(context);
    }

    /**
     *
     * @param context
     * @param attrs
     */
    public LinkView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     *
     * @param context
     * @param attrs
     * @param defStyleAttr
     */
    public LinkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     *
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setMovementMethod(LinkMovementMethod.getInstance());
    }

}
