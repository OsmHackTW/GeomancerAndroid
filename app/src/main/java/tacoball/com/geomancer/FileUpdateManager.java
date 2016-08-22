package tacoball.com.geomancer;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
     */
    public interface ProgressListener {
        /**
         * 新版本通知
         *
         * @param hasNew 是否有新版本
         * @param mtime  新版本的更新時間，格式為 UNIX Timestamp
         */
        void onCheckVersion(boolean hasNew, long mtime);

        /**
         * 下載進度通知
         *
         * @param step    第幾步驟
         * @param percent 這個步驟的百分比
         */
        void onNewProgress(int step, int percent);

        /**
         * 下載完成
         */
        void onComplete();

        /**
         * 已取消動作
         */
        void onCancel();

        /**
         * 錯誤通知
         *
         * @param step   發生錯誤的步驟
         * @param reason 錯誤原因
         */
        void onError(int step, String reason);

    }

    // 步驟值
    public static final int STEP_PREPARE  = 1; // 前置作業
    public static final int STEP_DOWNLOAD = 2; // 下載
    public static final int STEP_REPAIR   = 3; // 檢查與修復
    public static final int STEP_EXTRACT  = 4; // 解壓縮

    // 緩衝區用量
    private static final int BUFFER_SIZE = 8192;

    // 由外部供應資源
    private File saveTo;
    private long partsize;
    private ProgressListener listener;

    // 處理過程
    private long gzlen  = 0;  // 壓縮檔大小
    private long fixlen = 0;  // 需要修復的大小
    private long mtime  = 0;  // 最後更新時間
    private int step;         // 步驟值
    private MessageDigest md; // 摘要演算法，目前僅使用 MD5

    // 情境模擬參數
    private boolean forceDownloadFailed = true; // 模擬下載時發生錯誤
    private boolean forceRepairFailed = false;   // 模擬修復時發生錯誤

    // 執行中的子 Thread，僅限單工
    private Thread EMPTY_TASK = new Thread();
    private Thread workingTask = EMPTY_TASK;

    /**
     * 產生檔案更新管理機制 (精簡)
     *
     * @param saveTo 本地儲存路徑
     */
    public FileUpdateManager(File saveTo) {
        this(saveTo, 1048576L);
    }

    /**
     * 產生檔案更新管理機制 (完整)
     *
     * @param saveTo   本地儲存路徑
     * @param partsize 分段大小
     */
    public FileUpdateManager(File saveTo, long partsize) {
        this.saveTo = saveTo;
        this.partsize = partsize;
        this.step = STEP_PREPARE;

        try {
            md = MessageDigest.getInstance("MD5");
        } catch(NoSuchAlgorithmException ex) {
            // 應該不會發生
        }

        // 預設的事件處理器，輸出到 STDOUT
        listener = new ProgressListener() {

            @Override
            public void onCheckVersion(boolean hasNew, long mtime) {
                if (hasNew) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    String datestr = sdf.format(new Date(mtime));
                    System.out.printf("發現新版本: %s\n", datestr);
                } else {
                    System.out.println("不用更新");
                }
            }

            @Override
            public void onNewProgress(int step, int percent) {
                System.out.printf("步驟 %d: 進度 %d%%\n", step, percent);
            }

            @Override
            public void onComplete() {
                System.out.println("更新完成");
            }

            @Override
            public void onCancel() {
                System.out.println("已取消更新");
            }

            @Override
            public void onError(int step, String reason) {
                System.out.printf("發生錯誤: 步驟 %d, 原因 %s\n", step, reason);
            }

        };
    }

    /**
     * 取得遠端檔案資訊，會取得更新時間 (Last-Modified) 與檔案長度 (Content-Length)
     *
     * @param fileURL 檔案網址
     */
    private void getMetadata(String fileURL) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("HEAD");
        conn.connect();
        gzlen = conn.getContentLength();
        mtime = conn.getLastModified();
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
    private File getGzipFile(String fileURL) {
        int begin = fileURL.lastIndexOf('/') + 1;
        return new File(saveTo, fileURL.substring(begin));
    }

    /**
     * 取得解壓縮檔案
     *
     * @param fileURL 檔案網址
     * @return 解壓縮檔案
     */
    private File getExtractedFile(String fileURL) {
        int begin = fileURL.lastIndexOf('/') + 1;
        int end   = fileURL.lastIndexOf('.');
        return new File(saveTo, fileURL.substring(begin,end));
    }

    /**
     * 取得分段摘要值
     *
     * @param fileURL 檔案網址
     */
    private String[] getRemoteChecksum(String fileURL) throws IOException {
        boolean aborted = false;
        int cnt = getPartCount();
        String[] checksums = new String[cnt];

        String checksumURL = fileURL.substring(0, fileURL.lastIndexOf('.')) + ".md5";
        URL url = new URL(checksumURL);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.connect();

        try {
            InputStream  in  = conn.getInputStream();
            OutputStream out = new ByteArrayOutputStream();
            IOUtils.copyLarge(in, out);
            String[] lines = out.toString().split("\n");
            out.close();
            in.close();

            if (lines.length >= cnt) {
                checksums = new String[cnt];
                for (int i=0;i<cnt;i++) {
                    checksums[i] = lines[i].substring(0, 32);
                }
            }
        } catch(StringIndexOutOfBoundsException ex) {
            aborted = true;
        } catch(IOException ex) {
            aborted = true;
        }

        conn.disconnect();

        if (aborted) {
            throw new IOException("無法取得摘要值");
        }

        return checksums;
    }

    /**
     * 分段下載
     *
     * @param fileURL 檔案網址
     * @param number  分段編號
     * @param fixnum  已修復分段數 (僅修復模式需要用到)
     */
    private void downloadPart(String fileURL, int number, int fixnum) throws IOException {
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

            iocnt = in.read(buffer);

            // 可取消設計
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        out.close();
        in.close();
        conn.disconnect();
    }

    /**
     * 取得本地檔案分段摘要
     *
     * @param gzfile 本地檔案
     * @param number 分段順序
     * @return       摘要值 (Hex 字串)
     */
    private String getLocalPartChecksum(File gzfile, int number) throws IOException {
        FileInputStream   fis = new FileInputStream(gzfile);
        DigestInputStream dis = new DigestInputStream(fis, md);
        dis.skip(partsize * number);

        int readlen = 1;
        int total   = 0;
        byte[] buf = new byte[BUFFER_SIZE];
        while (readlen>0 && total<partsize) {
            readlen = dis.read(buf);
            total += readlen;
        }
        dis.close();

        StringBuilder sb = new StringBuilder();
        byte[] hash = md.digest();
        for (int i=0;i<hash.length;i++) {
            String bytestr = String.format("%02x", (hash[i]&0xff));
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
    private void repairTE(String fileURL, File gzfile) throws IOException {
        // 檢查損壞片段
        String actual;
        String[] expected = getRemoteChecksum(fileURL);
        ArrayList<Integer> crashed = new ArrayList<>();
        int cnt = getPartCount();
        for (int i=0;i<cnt;i++) {
            actual = getLocalPartChecksum(gzfile, i);
            if (!actual.equals(expected[i])) {
                crashed.add(i);
            }

            // 可取消設計
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
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
            int steelCrashed = 0;
            int fn = 0;
            for (int i : crashed) {
                downloadPart(fileURL, i, fn++);
                actual = getLocalPartChecksum(gzfile, i);
                if (!actual.equals(expected[i])) {
                    steelCrashed++;
                }

                // 可取消設計
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }

            if (steelCrashed>0) {
                throw new IOException("檔案修復失敗");
            }
        }
    }

    /**
     * 取得解壓縮後長度
     *
     * @param gzfile 壓縮檔
     * @return       解壓縮長度
     */
    private static long getExtractedLength(File gzfile) throws IOException {
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
    private void extract(File gzfile, File exfile) throws IOException {
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
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }

        in.close();
        out.close();
        gzfile.delete();
    }

    /**
     * 檢查新版本
     *
     * @param fileURL 檔案網址
     */
    public void checkVersion(final String fileURL) {
        synchronized(workingTask) {
            if (workingTask==EMPTY_TASK) {
                workingTask = new Thread() {
                    @Override
                    public void run() {
                        try {
                            File exfile = getExtractedFile(fileURL);
                            getMetadata(fileURL);
                            long localMtime = 0;
                            if (exfile.exists()) {
                                localMtime = exfile.lastModified();
                            }

                            workingTask = EMPTY_TASK;
                            listener.onCheckVersion((mtime>localMtime), mtime);
                        } catch(IOException ex) {
                            workingTask = EMPTY_TASK;
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
    public void update(final String fileURL) {
        synchronized(workingTask) {
            if (workingTask==EMPTY_TASK) {
                workingTask = new Thread() {
                    @Override
                    public void run() {
                        try {
                            listener.onNewProgress(step, 0);

                            // 移除殘留壓縮檔與目前檔案
                            File gzfile = getGzipFile(fileURL);
                            if (gzfile.exists()) {
                                gzfile.delete();
                            }
                            listener.onNewProgress(step, 25);

                            File exfile = getExtractedFile(fileURL);
                            if (exfile.exists()) {
                                exfile.delete();
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
                                if (Thread.currentThread().isInterrupted()) {
                                    throw new InterruptedException("");
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
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException("");
                            }

                            // 解壓縮
                            step = STEP_EXTRACT;
                            listener.onNewProgress(step, 0);
                            extract(gzfile, exfile);

                            // 取消點 3
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException("");
                            }

                            // 本地檔案 mtime 與遠端檔案同步
                            exfile.setLastModified(mtime);

                            workingTask = EMPTY_TASK;
                            listener.onComplete();
                        } catch(IOException ex) {
                            workingTask = EMPTY_TASK;
                            listener.onError(step, ex.getMessage());
                        } catch(InterruptedException ex) {
                            workingTask = EMPTY_TASK;
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
    public void repair(final String fileURL) {
        synchronized(workingTask) {
            if (workingTask==EMPTY_TASK) {
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

                            // 解壓縮
                            step = STEP_EXTRACT;
                            listener.onNewProgress(step, 0);
                            extract(gzfile, exfile);

                            // 本地檔案 mtime 與遠端檔案同步
                            exfile.setLastModified(mtime);

                            workingTask = EMPTY_TASK;
                            listener.onComplete();
                        } catch(IOException ex) {
                            workingTask = EMPTY_TASK;
                            listener.onError(step, ex.getMessage());
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
        synchronized(workingTask) {
            if (workingTask!=EMPTY_TASK) {
                workingTask.interrupt();
            }
        }
    }

    /**
     * 設定事件捕捉器，流程控制用
     *
     * @param listener 事件捕捉器
     */
    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }

    /**
     * 變更儲存位置
     *
     * @param saveTo 儲存位置
     */
    public void setSaveTo(File saveTo) {
        this.saveTo = saveTo;
    }

}