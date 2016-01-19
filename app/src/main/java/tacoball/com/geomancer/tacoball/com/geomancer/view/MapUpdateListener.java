package tacoball.com.geomancer.tacoball.com.geomancer.view;

/**
 * Created by raymond on 16/1/15.
 */
public interface MapUpdateListener {

    void onDownload(int percent);

    void onDecompress(int percent);

}
