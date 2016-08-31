package tacoball.com.geomancer.checkupdate;

import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * 檔案更新管理機制
 *
 * - 新版本檢查
 * - 下載流程管理
 * - 進度通知
 * - 可取消設計
 * - 防止重複執行設計
 */
public class FileUpdateManager {

    /**
     * 進度接收介面
     * 採用懶人設計，沒興趣接收的事件可以不用實作
     */
    public static class ProgressListener {

        protected boolean enableStdout = false;

        /**
         * 繼承用
         */
        public ProgressListener() { }

        /**
         * 預設進度接受器用
         *
         * @param enableStdout 是否開啟主控台輸出
         */
        public ProgressListener(boolean enableStdout) {
            this.enableStdout = enableStdout;
        }

        /**
         * 新版本通知
         *
         * @param length 新版本的檔案大小, 0 表示不用更新
         * @param mtime  新版本的更新時間，格式為 UNIX Timestamp
         */
        public void onCheckVersion(long length, long mtime) {
            if (enableStdout) {
                if (length>0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    String datestr = sdf.format(new Date(mtime));
                    System.out.printf("發現新版本: %s\n", datestr);
                } else {
                    System.out.println("不用更新");
                }
            }
        }

        /**
         * 下載進度通知
         *
         * @param step    第幾步驟
         * @param percent 這個步驟的百分比
         */
        public void onNewProgress(int step, int percent) {
            if (enableStdout) {
                System.out.printf("步驟 %d: 進度 %d%%\n", step, percent);
            }
        }

        /**
         * 下載完成
         */
        public void onComplete() {
            if (enableStdout) {
                System.out.println("更新完成");
            }
        }

        /**
         * 已取消動作
         */
        public void onCancel() {
            if (enableStdout) {
                System.out.println("已取消更新");
            }
        }

        /**
         * 錯誤通知
         *
         * @param step   發生錯誤的步驟
         * @param reason 錯誤原因
         */
        public void onError(int step, String reason) {
            if (enableStdout) {
                System.out.printf("發生錯誤: 步驟 %d, 原因 %s\n", step, reason);
            }
        }

    }

    // 步驟值
    public static final int STEP_PREPARE  = 1; // 前置作業
    public static final int STEP_DOWNLOAD = 2; // 下載
    public static final int STEP_REPAIR   = 3; // 檢查與修復
    public static final int STEP_EXTRACT  = 4; // 解壓縮

    // 緩衝區用量
    private static final int BUFFER_SIZE = 8192;

    // 情境模擬參數
    public static boolean forceDownloadFailed = false; // 模擬下載時發生錯誤
    public static boolean forceRepairFailed   = false; // 模擬修復時發生錯誤

    // 由外部供應資源
    private File saveTo;
    private long partsize;
    private ProgressListener listener;
    private ProgressListener defaultListener;

    // 處理過程
    private long gzlen  = 0;  // 壓縮檔大小
    private long fixlen = 0;  // 需要修復的大小
    private long mtime  = 0;  // 最後更新時間
    private int  step;        // 步驟值
    private String[] partsum; // 下載壓縮檔每個片段的摘要值
    private MessageDigest md; // 摘要演算法，目前僅使用 MD5

    // 執行中的子 Thread，僅限單工
    private final Object TASK_LOCK = new Object();
    private Thread workingTask;
    private boolean canceled = false;

    /**
     * 產生檔案更新管理機制 (精簡)
     */
    public FileUpdateManager() {
        this(1048576L);
    }

    /**
     * 產生檔案更新管理機制 (完整)
     *
     * @param partsize 分段大小
     */
    public FileUpdateManager(final long partsize) {
        this.partsize = partsize;
        this.step = STEP_PREPARE;

        try {
            md = MessageDigest.getInstance("MD5");
        } catch(NoSuchAlgorithmException ex) {
            // 應該不會發生
        }

        // 預設的事件處理器，輸出到主控台
        defaultListener = new ProgressListener(true);
        listener = defaultListener;
    }

    /**
     * 取得遠端檔案資訊
     * - 取得修改時間 (Last-Modified)
     * - 取得檔案長度 (Content-Length)
     * - 取得遠端壓縮檔每 1MB 片段的 MD5
     *
     * @param fileURL 檔案網址
     */
    private void getMetadata(final String fileURL) throws IOException {
        // 取得修改時間與長度
        URL url = new URL(fileURL);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("HEAD");
        conn.connect();

        int resp = conn.getResponseCode();
        if (resp == 200) {
            gzlen = conn.getContentLength();
            mtime = conn.getLastModified();
        }
        conn.disconnect();

        if (resp != 200) {
            throw new IOException(String.format(Locale.getDefault(), "HTTP %d", resp));
        }

        // 取得壓縮檔每 1MB 片段的 MD5
        int cnt = getPartCount();
        partsum = new String[cnt];
        String checksumURL = fileURL.substring(0, fileURL.lastIndexOf('.')) + ".pmd5";
        url  = new URL(checksumURL);
        conn = (HttpURLConnection)url.openConnection();
        conn.connect();
        BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));

        for (int i=0;i<cnt;i++) {
            partsum[i] = r.readLine();
        }

        r.close();
        conn.disconnect();
    }

    /**
     * 取得分段數量
     *
     * @return 分段數量
     */
    private int getPartCount() {
        return (int)((gzlen+partsize-1)/partsize);
    }

    /**
     * 取得 gzip 檔案
     *
     * @param fileURL 檔案網址
     * @return gzip 檔案
     */
    private File getGzipFile(final String fileURL) {
        int begin = fileURL.lastIndexOf('/') + 1;
        return new File(saveTo, fileURL.substring(begin));
    }

    /**
     * 取得解壓縮檔案
     *
     * @param fileURL 檔案網址
     * @return 解壓縮檔案
     */
    private File getExtractedFile(final String fileURL) {
        int begin = fileURL.lastIndexOf('/') + 1;
        int end   = fileURL.lastIndexOf('.');
        return new File(saveTo, fileURL.substring(begin,end));
    }

    /**
     * 取得 MD5 檔案
     *
     * @param fileURL 檔案網址
     * @return MD5 檔案
     */
    private File getChecksumFile(final String fileURL) {
        int begin = fileURL.lastIndexOf('/') + 1;
        int end   = fileURL.lastIndexOf('.');
        return new File(saveTo, fileURL.substring(begin,end) + ".lmd5");
    }

    /**
     * 下載檔案片段
     *
     * @param fileURL 檔案網址
     * @param number  分段編號
     * @param fixnum  已修復片段數 (僅修復模式需要用到)
     */
    private void downloadPart(final String fileURL, final int number, final int fixnum) throws IOException {
        // 故意讓 3, 8, 13, 18, ... 等片段發生錯誤
        if (forceDownloadFailed && (number%5)==3) {
            return;
        }

        long begin = number * partsize;
        long end   = begin + partsize - 1;
        String range = String.format(Locale.getDefault(), "bytes=%d-%d", begin, end);
        File gzfile = getGzipFile(fileURL);

        URL url = new URL(fileURL);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestProperty("Range", range);
        conn.connect();

        InputStream      in  = conn.getInputStream();
        RandomAccessFile out = new RandomAccessFile(gzfile, "rw");
        out.seek(begin);

        byte[] buffer = new byte[BUFFER_SIZE];
        int iocnt = in.read(buffer);
        int percent = 0;
        long copied = (step==STEP_DOWNLOAD) ? number*partsize : fixnum*partsize;
        while (iocnt > 0) {
            out.write(buffer, 0, iocnt);

            if (step == STEP_DOWNLOAD) {
                copied += iocnt;
                int np = (int)(copied*100/gzlen);
                if (np > percent) {
                    percent = np;
                    listener.onNewProgress(step, percent);
                }
            }

            if (step == STEP_REPAIR) {
                copied += iocnt;
                int np = (int)(copied*100/fixlen);
                if (np > percent) {
                    percent = np;
                    listener.onNewProgress(step, percent);
                }
            }

            // 可取消設計
            if (canceled) break;

            // Thread 中斷後這個方法會產生 IOException，會導致取消作業不正常，所以一定要放在取消設計之後
            iocnt = in.read(buffer);
        }

        out.close();
        in.close();
        conn.disconnect();
    }

    /**
     * 下載解壓縮後檔案最後 1MB 的 MD5
     *
     * @param fileURL 檔案網址
     */
    private void downloadLastPartMD5(String fileURL) throws IOException {
        URL url = new URL(fileURL.substring(0, fileURL.lastIndexOf('.')) + ".lmd5");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.connect();
        InputStream in = conn.getInputStream();

        File f = getChecksumFile(fileURL);
        OutputStream out = new FileOutputStream(f);

        IOUtils.copy(in, out);

        in.close();
        out.close();
        conn.disconnect();
    }

    /**
     * 取得解壓縮檔案最後 1MB 的 MD5
     *
     * @param fileURL 檔案網址
     * @return        最後 1MB 的 MD5
     */
    private String getLastPartChecksum(String fileURL) throws IOException {
        BufferedReader r = new BufferedReader(new FileReader(getChecksumFile(fileURL)));
        String checksum = r.readLine();
        r.close();
        return checksum;
    }

    /**
     * 取得本地檔案分段摘要
     *
     * @param gzfile 本地檔案
     * @param number 分段順序
     * @return       摘要值 (Hex 字串)
     */
    private String getLocalPartChecksum(final File gzfile, final int number) throws IOException {
        FileInputStream   fis = new FileInputStream(gzfile);
        DigestInputStream dis = new DigestInputStream(fis, md);

        long off = partsize * number;
        if (dis.skip(off) < off) {
            dis.close();
            fis.close();
            throw new IOException("Cannot move to offset position.");
        }

        int readlen = 1;
        int total   = 0;
        byte[] buf = new byte[BUFFER_SIZE];
        while (readlen>0 && total<partsize) {
            readlen = dis.read(buf);
            total += readlen;
        }
        dis.close();
        fis.close();

        StringBuilder sb = new StringBuilder();
        for (byte hash : md.digest()) {
            String bytestr = String.format("%02x", (hash&0xff));
            sb.append(bytestr);
        }

        String result = sb.toString();
        sb.delete(0, sb.length());

        return result;
    }

    /**
     * 修復檔案
     *
     * @param fileURL 檔案網址
     * @param gzfile  本地檔案
     */
    private void repairTE(final String fileURL, final File gzfile) throws IOException {
        // 檢查損壞片段
        String actual;
        ArrayList<Integer> crashed = new ArrayList<>();
        int cnt = getPartCount();
        for (int i=0;i<cnt;i++) {
            actual = getLocalPartChecksum(gzfile, i);
            if (!actual.equals(partsum[i])) {
                crashed.add(i);
            }

            // 可取消設計
            if (canceled) return;
        }

        // 計算需要修復的長度，用來計算修復進度
        fixlen = 0;
        if (crashed.contains(cnt-1)) {
            fixlen = gzlen % partsize;
        } else {
            fixlen = partsize;
        }
        fixlen += (crashed.size()-1) * partsize;

        // 修復損壞片段
        if (crashed.size()>0) {
            int stillCrashed = 0;
            int fn = 0;
            for (int i : crashed) {
                downloadPart(fileURL, i, fn++);
                actual = getLocalPartChecksum(gzfile, i);
                if (!actual.equals(partsum[i])) {
                    stillCrashed++;
                }

                // 可取消設計
                if (canceled) return;
            }

            if (stillCrashed>0) {
                throw new IOException("檔案修復失敗");
            }
        }
    }

    /**
     * 取得解壓縮後長度
     * (僅供 extract 使用，作為進度計算的分母)
     *
     * @param gzfile 壓縮檔
     * @return       解壓縮長度
     */
    private static long getExtractedLength(final File gzfile) throws IOException {
        long size = 0;

        RandomAccessFile raf = new RandomAccessFile(gzfile, "r");
        raf.seek(raf.length()-4);
        for (int i=0;i<4;i++) {
            size = size | (raf.read()<<(i*8));
        }
        raf.close();

        return size;
    }

    /**
     * gzip 檔案解壓縮
     *
     * @param gzfile 壓縮檔
     * @param exfile 解壓縮檔
     */
    private void extract(final File gzfile, final File exfile) throws IOException {
        InputStream  in  = new GZIPInputStream(new FileInputStream(gzfile));
        OutputStream out = new FileOutputStream(exfile);

        long iocnt = 1;
        long exlen = getExtractedLength(gzfile);
        long extracted = 0;
        int percent = 0;
        while (iocnt>0) {
            iocnt = IOUtils.copyLarge(in, out, 0, BUFFER_SIZE);
            extracted += iocnt;
            int np = (int)(extracted*100/exlen);
            if (np > percent) {
                percent = np;
                listener.onNewProgress(step, percent);
            }
            out.flush();

            // 可取消設計
            if (canceled) break;
        }

        in.close();
        out.close();

        if (canceled) {
            if (!exfile.delete()) {
                throw new IOException("Cannot delete file after canceled.");
            }
        } else {
            if (!gzfile.delete()) {
                throw new IOException("Cannot delete file after extracted.");
            }
        }
    }

    /**
     * 檢查檔案是否必要更新
     * (如果處於必要更新狀態，可以設計成不出現更新通知)
     *
     * @param  fileURL  檔案遠端位置
     * @param  saveTo   儲存位置
     * @param  mtimeMin 檔案最小時間限制
     * @return 是否必要更新
     */
    public boolean isRequired(final String fileURL, final File saveTo, final long mtimeMin) {
        this.saveTo = saveTo;
        File exfile = getExtractedFile(fileURL);

        // 檔案存在
        if (!exfile.exists() || exfile.length()==0) {
            return true;
        }

        // 檔案版本高於最低要求
        if (exfile.lastModified()<mtimeMin) {
            return true;
        }

        // MD5 存在
        File f = getChecksumFile(fileURL);
        if (!f.exists()) {
            return true;
        }

        // 最後 1MB MD5 正確，避免應用程式無預警錯誤
        try {
            int lastpart = (int)((exfile.length()-1) / partsize);
            String md5actual   = getLocalPartChecksum(exfile, lastpart);
            String md5expected = getLastPartChecksum(fileURL);
            if (!md5actual.equals(md5expected)) {
                return true;
            }
        } catch(IOException ex) {
            listener.onError(step, ex.getMessage());
        }

        return false;
    }

    /**
     * 檢查新版本
     *
     * @param fileURL 檔案網址
     */
    public void checkVersion(final String fileURL, final File saveTo) {
        synchronized(TASK_LOCK) {
            if (workingTask==null) {
                this.saveTo = saveTo;

                workingTask = new Thread() {
                    @Override
                    public void run() {
                        try {
                            File exfile = getExtractedFile(fileURL);
                            getMetadata(fileURL);
                            long txlen = gzlen;

                            if (exfile.exists() && mtime <= exfile.lastModified()) {
                                txlen = 0;
                            }

                            workingTask = null;
                            listener.onCheckVersion(txlen, mtime);
                        } catch(IOException ex) {
                            workingTask = null;
                            listener.onError(step, ex.getMessage());
                        }
                    }
                };
                workingTask.start();
            } else {
                listener.onError(step, "正在執行其他動作，無法檢查版本");
            }
        }
    }

    /**
     * 更新檔案
     *
     * @param fileURL 檔案網址
     */
    public void update(final String fileURL, final File saveTo) {
        synchronized(TASK_LOCK) {
            if (workingTask==null) {

                this.saveTo = saveTo;

                workingTask = new Thread() {
                    @Override
                    public void run() {
                        try {
                            listener.onNewProgress(step, 0);

                            // 移除殘留壓縮檔與目前檔案
                            File gzfile = getGzipFile(fileURL);
                            if (gzfile.exists()) {
                                if (!gzfile.delete()) {
                                    throw new IOException("Cannot delete old gz file.");
                                }
                            }
                            listener.onNewProgress(step, 25);

                            File exfile = getExtractedFile(fileURL);
                            if (exfile.exists()) {
                                if (!exfile.delete()) {
                                    throw new IOException("Cannot delete old file.");
                                }
                            }
                            listener.onNewProgress(step, 50);

                            // 取得線上版本的更新日期與長度
                            // 如果這動作已經被 checkVersion() 處理了就跳過
                            if (mtime==0) {
                                getMetadata(fileURL);
                            }
                            listener.onNewProgress(step, 100);

                            // 下載所有分割
                            // (失敗時不會中斷，也不會產生錯誤訊息)
                            step = STEP_DOWNLOAD;
                            listener.onNewProgress(step, 0);
                            int cnt = getPartCount();
                            for (int i=0;i<cnt;i++) {
                                downloadPart(fileURL, i, 0);

                                // 取消點 1
                                if (canceled) {
                                    throw new InterruptedException("在下載階段取消更新");
                                }
                            }

                            // 錯誤狀況模擬
                            if (forceDownloadFailed) {
                                if (!forceRepairFailed) {
                                    forceDownloadFailed = false;
                                }
                            }

                            // 檢查與自動修復
                            step = STEP_REPAIR;
                            listener.onNewProgress(step, 0);
                            repairTE(fileURL, gzfile);

                            // 取消點 2
                            if (canceled) {
                                throw new InterruptedException("在修復階段取消更新");
                            }

                            // 下載解壓縮後檔案最後 1MB 的 MD5
                            downloadLastPartMD5(fileURL);

                            // 解壓縮
                            step = STEP_EXTRACT;
                            listener.onNewProgress(step, 0);
                            extract(gzfile, exfile);

                            // 取消點 3
                            if (canceled) {
                                throw new InterruptedException("在解壓縮階段取消更新");
                            }

                            // 本地檔案 mtime 與遠端檔案同步
                            if (!exfile.setLastModified(mtime)) {
                                throw new IOException("Cannot set mtime.");
                            }

                            workingTask = null;
                            listener.onComplete();
                        } catch(IOException ex) {
                            workingTask = null;
                            listener.onError(step, ex.getMessage());
                        } catch(InterruptedException ex) {
                            workingTask = null;
                            listener.onCancel();
                        }
                    }
                };
                workingTask.start();
            } else {
                listener.onError(step, "正在執行其他動作，無法更新檔案");
            }
        }
    }

    /**
     * 修復檔案
     *
     * @param fileURL 檔案網址
     */
    public void repair(final String fileURL, final File saveTo) {
        synchronized(TASK_LOCK) {
            if (workingTask==null) {
                this.saveTo = saveTo;

                workingTask = new Thread() {
                    @Override
                    public void run() {
                        try {
                            // 前置作業
                            step = STEP_PREPARE;
                            listener.onNewProgress(step, 0);

                            File gzfile = getGzipFile(fileURL);
                            File exfile = getExtractedFile(fileURL);
                            if (!gzfile.exists() && exfile.exists()) {
                                throw new IOException("檔案已完成更新，不需要修復");
                            }

                            // 取得線上版本的更新日期與長度
                            // 如果這動作已經被 checkVersion() 處理了就跳過
                            if (mtime==0) {
                                getMetadata(fileURL);
                            }
                            listener.onNewProgress(step, 100);

                            if (forceRepairFailed) {
                                forceDownloadFailed = false;
                            }

                            // 檢查與自動修復
                            step = STEP_REPAIR;
                            listener.onNewProgress(step, 0);
                            repairTE(fileURL, gzfile);

                            // 取消點 1
                            if (canceled) {
                                throw new InterruptedException("在修復階段取消更新");
                            }

                            // 下載解壓縮後檔案最後 1MB 的 MD5
                            downloadLastPartMD5(fileURL);

                            // 解壓縮
                            step = STEP_EXTRACT;
                            listener.onNewProgress(step, 0);
                            extract(gzfile, exfile);

                            // 取消點 2
                            if (canceled) {
                                throw new InterruptedException("在解壓縮階段取消更新");
                            }

                            // 本地檔案 mtime 與遠端檔案同步
                            if (!exfile.setLastModified(mtime)) {
                                throw new IOException("Cannot set mtime.");
                            }

                            workingTask = null;
                            listener.onComplete();
                        } catch(IOException ex) {
                            workingTask = null;
                            listener.onError(step, ex.getMessage());
                        } catch(InterruptedException ex) {
                            workingTask = null;
                            listener.onCancel();
                        }
                    }
                };
                workingTask.start();
            }
        }
    }

    /**
     * 取消檔案更新或修復
     */
    public void cancel() {
        synchronized(TASK_LOCK) {
            if (workingTask!=null) {
                canceled = true;
                workingTask.interrupt();
            }
        }
    }

    /**
     * 設定事件捕捉器，流程控制用
     *
     * @param listener 事件捕捉器
     */
    public void setListener(final ProgressListener listener) {
        this.listener = listener;
    }

    /**
     * 解除事件捕捉器，流程控制用
     */
    public void unsetListener() {
        listener = defaultListener;
    }

}
