package tacoball.com.geomancer.checkupdate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 檔案更新管理程式
 * 
 * @author 小璋丸 <virus.warnning@gmail.com>
 */
public class AutoUpdateManager {

	// 外部提供的參數
	private String baseUrl;
	private File logPath;
	private File filePath;
	private Map<String, File> movePath;
	private List<AutoUpdateAdapter> listenerList;
	
	// 更新資訊以及更新紀錄
	private Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private JsonObject updateInfo;
	private JsonObject previousUpdateLog;
	private JsonObject currentUpdateLog;
	
	// 每一個更新項目用到的值
	private String currentFilename;
	private URL currentUrl;
	private File currentFile;
	private File currentGzFile;
	private JsonObject currentFileInfo;

	// 執行緒管理
	private Thread  updateThread;
	private Thread  checkThread;
	private boolean userCanceled;

	// 片段設定值
	private static String digest     = "MD5";   // 摘要演算法
	private static int    partLength = 1048576; // 片段大小
	
	// 緩衝區 (大小必須是 PART_SIZE 的因數)
	// 共用緩衝區空間會用在 "摘要計算" 與 "片段下載"，"解壓縮" 則使用 IOUtils
	private static final int BUFFER_SIZE = 32768;
	private static final byte[] BUFFER = new byte[BUFFER_SIZE];
	
	/**
	 * 配置檔案更新總管
	 *
	 * @param logPath  紀錄檔存放位置
	 * @param filePath 下載檔預設存放位置
	 */
	public AutoUpdateManager(File logPath, File filePath) {
		this.logPath = logPath;
		this.filePath = filePath;
		movePath = new HashMap<>();
		listenerList = new ArrayList<>();
	}

	/**
	 * 新增更新事件接收器，用來顯示更新進度
	 * 
	 * @param listener 更新事件接收器
	 */
	public void addListener(AutoUpdateAdapter listener) {
		listenerList.add(listener);
	}

	/**
	 * 指定儲存路徑
	 */
	public void saveTo(String filename, File path) {
		movePath.put(filename, path);
	}
	
	/**
	 * 啟動更新流程，會開啟另一個執行緒進行檔案更新，不阻斷現有工作
	 */
	public void start(String url) {
		if (updateThread != null) {
			trigger.onError("不可同時進行兩個以上的更新");
			return;
		}

		// URL 防呆處理
		baseUrl = url;
		if (!baseUrl.endsWith("/")) {
			baseUrl = baseUrl + "/";
		}

		// 啟動非同步更新流程
		updateThread = new Thread() {
			public void run() {
				try {
					// 下載更新資訊與轉換檔案清單
					Set<Entry<String, JsonElement>> fileSet;
					updateInfo = loadJSON(baseUrl + "/update.json");
					digest = updateInfo.getAsJsonObject("config").get("digest").getAsString();
					partLength = updateInfo.getAsJsonObject("config").get("partLength").getAsInt();
					fileSet = updateInfo.getAsJsonObject("files").entrySet();
					interruptUpdate("下載更新資訊後", false);
					
					// 載入先前更新紀錄，沒有則產生一個空結構維持正常運作
					try {
						previousUpdateLog = loadJSON(logPath + "/update-log.json");
					} catch(InterruptedException ex) {
						// 指定一個無效的檔案規格，避免 NPE 發生
						JsonObject config = new JsonObject();
						config.addProperty("spec", "0.0.0");
						
						previousUpdateLog = new JsonObject();
						previousUpdateLog.add("files", new JsonObject());
						previousUpdateLog.add("config", config);
					}
					interruptUpdate("載入更新紀錄後", false);
					
					// 配置本次更新紀錄
					currentUpdateLog = new JsonObject();
					currentUpdateLog.add("config", gson.fromJson(updateInfo.getAsJsonObject("config"), JsonObject.class));
					
					// 更新檔規格檢查
					String remoteSpec = updateInfo.getAsJsonObject("config").get("spec").getAsString();
					String localSpec  = previousUpdateLog.getAsJsonObject("config").get("spec").getAsString();
					if (remoteSpec.equals(localSpec)) {
						// 檔案規格相同，採納先前更新紀錄跳過不用更新的檔案
						currentUpdateLog.add("files", gson.fromJson(previousUpdateLog.getAsJsonObject("files"), JsonObject.class));
					} else {
						// 檔案規格不同，不採納先前更新紀錄
						currentUpdateLog.add("files", new JsonObject());
					}
					
					// 依序更新每一個檔案
					for (Entry<String, JsonElement> fileElement : fileSet) {
						// 計算應儲存位置
						currentFilename = fileElement.getKey();
						File path = movePath.containsKey(currentFilename) ? movePath.get(currentFilename) : filePath;
						
						// 準備更新時需要的狀態值
						currentFileInfo = updateInfo.getAsJsonObject("files").getAsJsonObject(currentFilename);
						currentFile = new File(path, currentFilename);
						currentGzFile = new File(path, currentFilename + ".gz");
						try {
							currentUrl = new URL(baseUrl + currentFilename + ".gz");
						} catch(MalformedURLException ex) {
							String reason = String.format("更新資訊網址格式錯誤 (%s)", baseUrl + currentFilename + ".gz");
							interruptUpdate(reason, true);
						}
						
						// 更新檔案
						update();
						
						// 儲存更新紀錄
						saveJSON(logPath + "/update-log.json", currentUpdateLog);
					}
					
					// 回報全部完成
					trigger.onComplete();
				} catch(InterruptedException ex) {
					if (userCanceled) {
						trigger.onUserCancel("更新作業已取消：" + ex.getMessage());
					} else {
						trigger.onError("更新作業異常終止：" + ex.getMessage());
					}
				}
			}
		};
		updateThread.start();
	}
	
	/**
	 * 檢查是否需要更新
	 */
	public void checkUpdate(String url, final CheckUpdateAdapter adapter) {
		// URL 防呆處理
		baseUrl = url;
		if (!baseUrl.endsWith("/")) {
			baseUrl = baseUrl + "/";
		}
		
		checkThread = new Thread() {
			public void run() {
				try {
					// 下載更新資訊與轉換檔案清單
					Set<Entry<String, JsonElement>> fileSet;
					updateInfo = loadJSON(baseUrl + "/update.json");
					digest = updateInfo.getAsJsonObject("config").get("digest").getAsString();
					partLength = updateInfo.getAsJsonObject("config").get("partLength").getAsInt();
					fileSet = updateInfo.getAsJsonObject("files").entrySet();
					
					// 載入先前更新紀錄，沒有則產生一個空結構維持正常運作
					try {
						previousUpdateLog = loadJSON(logPath + "/update-log.json");
					} catch(InterruptedException ex) {
						// 指定一個無效的檔案規格，避免 NPE 發生
						JsonObject config = new JsonObject();
						config.addProperty("spec", "0.0.0");
						previousUpdateLog = new JsonObject();
						previousUpdateLog.add("files", new JsonObject());
						previousUpdateLog.add("config", config);
					}
					
					// 計算需要更新的檔案大小
					long totalLength = 0;
					String lastModified = "";
					JsonObject localFiles = previousUpdateLog.get("files").getAsJsonObject();
					for (Entry<String, JsonElement> fileElement : fileSet) {
						// 1. 更新紀錄不存在需要更新              (常規檢查)
						// 2. 更新紀錄 mtime 異動時需要更新
						// 3. 檔案不存在需要更新                  (破壞檢查)
						// 4. 檔案 checksum 與計算結果不符需要更新
						boolean latest   = false;
						String  filename = fileElement.getKey();
						JsonObject rmtFile = fileElement.getValue().getAsJsonObject();
						
						if (localFiles.has(filename)) {
							JsonObject locFile = localFiles.getAsJsonObject(filename);
							long rmtMtime = rmtFile.get("mtime").getAsLong();
							long locMtime = locFile.get("mtime").getAsLong();
							if (rmtMtime <= locMtime) {
								String locChecksum = locFile.get("checksum").getAsString();
								File path = movePath.containsKey(filename) ? movePath.get(filename) : filePath;
								File f = new File(path, filename);
								if (f.exists()) {
									int partNumber = (int)((f.length() - 1) / partLength);
									if (checkPart(f, partNumber, locChecksum)) {
										latest = true;	
									}	
								}
							}
						}
						
						if (!latest) {
							totalLength += rmtFile.get("length").getAsLong();
							String date = rmtFile.get("isoTime").getAsString();
							if (date.compareTo(lastModified) > 0) {
								lastModified = date;
							}
						}
					}
					
					adapter.onCheck(totalLength, lastModified);
				} catch(InterruptedException ex) {
					adapter.onError(ex.getMessage());
				}
			}
		};
		checkThread.start();
	}
	
	/**
	 * 回傳上次更新到現在的天數
	 * 
	 * @return 上次更新到現在的天數
	 */
	public int getDaysFromLastUpdate() {
		long lastModified = 0;
		File f = new File(logPath, "update-log.json");
		if (f.exists()) {
			lastModified = f.lastModified();
		}
		
		int days = (int)((System.currentTimeMillis() - lastModified) / 86400000);
		return days;
	}
	
	/**
	 * 破壞指定檔案的 mtime，用來測試檢查更新效果
	 * 
	 * @param filename 要破壞 mtime 的檔案名稱
	 */
	public void damageMtime(String filename) {
		try {
			JsonObject json = loadJSON(logPath + "/update-log.json");
			JsonObject fileNode = json.getAsJsonObject("files").getAsJsonObject(filename);
			long mtime = fileNode.get("mtime").getAsLong();
			fileNode.remove("mtime");
			fileNode.addProperty("mtime", mtime-1);
			saveJSON(logPath + "/update-log.json", json);
		} catch(InterruptedException ex) {
			System.err.println(ex.getMessage());
		}
	}
	
	/**
	 * 等待更新程式結束，僅供單元測試等待答案用
	 */
	public void waitUntilComplete() {
		if (updateThread != null) {
			try {
				updateThread.join();
			} catch(InterruptedException ex) {
				System.err.println("更新程式中斷");
			}	
		} else {
			// System.err.println("尚未啟動更新程式");
		}
		
		if (checkThread != null) {
			try {
				checkThread.join();
			} catch(InterruptedException ex) {
				System.err.println("檢查程式中斷");
			}
		} else {
			// System.err.println("尚未啟動檢查程式");
		}
	}

	/**
	 * 取消更新流程
	 */
	public void cancel() {
		userCanceled = true;
	}
	
	/**
	 * 離線檢查現有檔案是否有用，如果堪用就不勉強更新
	 * 
	 * @param expectedSpec 應用程式需要的檔案規格
	 */
	public boolean isUseful(String expectedSpec) {
		// 檢查更新紀錄是否存在
		try {
			previousUpdateLog = loadJSON(logPath + "/update-log.json");
		} catch(InterruptedException ex) {
			return false;
		}
		
		// 檢查檔案規格是否與應用程式要求的規格一致
		// 這會發生在應用程式已升級，需要使用新的檔案但是檔案還沒升級
		String actualSpec = previousUpdateLog.getAsJsonObject("config").get("spec").getAsString();
		if (!expectedSpec.equals(actualSpec)) {
			return false;
		}

		// 檢查檔案數目是否正確，避免更新不完全而啟動應用程式
		int fileCount = previousUpdateLog.getAsJsonObject("config").get("fileCount").getAsInt();
		if (previousUpdateLog.getAsJsonObject("files").size() < fileCount) {
			return false;
		}
		
		// 檢查檔案是否存在與摘要是否正確
		// 可能發生在 SD 卡受損時
		Set<Entry<String, JsonElement>> files = previousUpdateLog.getAsJsonObject("files").entrySet();
		for (Entry<String, JsonElement> e : files) {
			String filename = e.getKey();
			String checksum = e.getValue().getAsJsonObject().get("checksum").getAsString();
			
			File savePath = movePath.containsKey(filename) ? movePath.get(filename) : filePath;
			File file = new File(savePath, filename);
			int  partNumber = (int)((file.length() - 1) / partLength);    
			
			try {
				if (!file.exists() || !checkPart(file, partNumber, checksum)) {
					return false;
				}
			} catch(InterruptedException ex) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * 中斷更新動作
	 *
	 * @param reason      中斷更新原因
	 * @param selfAborted 是否因更新過程錯誤
	 */
	private void interruptUpdate(String reason, boolean selfAborted) throws InterruptedException {
		if (userCanceled || selfAborted) {
			throw new InterruptedException(reason);
		}
	}
	
	/**
	 * 更新一個檔案
	 */
	private void update() throws InterruptedException {
		trigger.onFileBegin(currentFilename);

		if (isExpired()) {
			final int FILE_RETRY_LIMIT = 3;
			int fileRetry = -1; // 第 0 次執行不算 retry，所以起始值應該設 -1
			boolean validated = false;
			
			// 檔案重試迴圈
			while (fileRetry < FILE_RETRY_LIMIT && !validated) {
				// 下載壓縮檔 (局部容錯)
				int partNumber = 0;
				boolean hasNextPart;
				do {
					boolean partOk;
					String expected = currentFileInfo.getAsJsonArray("partChecksums").get(partNumber).getAsString();
					
					// 下載片段
					hasNextPart = downloadPart(partNumber);
					partOk = checkPart(currentGzFile, partNumber, expected);
					
					// 片段自動修復 (容錯)
					final int PART_RETRY_LIMIT = 3;
					int partRetry = 0;
					while (!partOk && partRetry < PART_RETRY_LIMIT) {
						String reason = String.format(Locale.getDefault(),
							"傳輸片段 #%d 發現內容損毀", partNumber
						);
						trigger.onFileWarning(currentFilename, reason);
						downloadPart(partNumber);
						partOk = checkPart(currentGzFile, partNumber, expected);
						partRetry++;
					}

					// 重試依然失敗 (不容錯)
					if (!partOk) {
						String reason = String.format(Locale.getDefault(),
							"傳輸片段 #%d 發現內容損毀，嘗試修復 %d 次無效",
							partNumber, PART_RETRY_LIMIT
						);
						interruptUpdate(reason, true);
					}
					
					partNumber++;
				} while(hasNextPart);
				
				// 移除舊檔 (不容錯)
				if (currentFile.exists()) {
					if (!currentFile.delete()) {
						interruptUpdate("檔案系統權限不足，無法刪除舊檔", true);
					}
				}
				
				// 解壓縮 (不容錯)
				extract();		
				
				// 移除壓縮檔 (不容錯)
				if (!currentGzFile.delete()) {
					interruptUpdate("檔案系統權限不足，無法刪除壓縮檔", true);
				}
				
				// 檢查解壓縮後最後一個片段的 MD5
				String expected = currentFileInfo.get("checksum").getAsString();
				partNumber = (int)((currentFile.length() - 1) / partLength);
				validated = checkPart(currentFile, partNumber, expected);
				if (!validated) {
					trigger.onFileWarning(currentFilename, "解壓縮後偵測到檔案損毀");
				}
				
				fileRetry++;
			}
			
			if (validated) {
				// 紀錄檔案更新資訊
				JsonObject logInfo = new JsonObject();
				logInfo.addProperty("mtime", currentFileInfo.get("mtime").getAsLong());
				logInfo.addProperty("checksum", currentFileInfo.get("checksum").getAsString());
				logInfo.addProperty("isoTime", currentFileInfo.get("isoTime").getAsString());
				currentUpdateLog.getAsJsonObject("files").add(currentFilename, logInfo);
				trigger.onFileComplete(currentFilename, true);
			}
		} else {
			trigger.onFileComplete(currentFilename, false);
		}
	}
	
	/**
	 * 泛用 JSON 載入程式
	 * 
	 * @param location JSON 所在位置
	 * @return JSON 物件
	 */
	private JsonObject loadJSON(String location) throws InterruptedException {
		StringWriter out = new StringWriter();

		try {
			if (location.startsWith("http://") || location.startsWith("http://")) {
				// 從 Web 讀取
				URL url = new URL(location);
				HttpURLConnection conn = (HttpURLConnection)url.openConnection();
				InputStream in = conn.getInputStream();
				IOUtils.copy(in, out, "UTF-8");
				in.close();
			} else {
				// 從檔案系統讀取
				FileReader in = new FileReader(location);
				IOUtils.copy(in, out);
				in.close();
			}
	
			out.close();
		} catch(IOException ex) {
			String reason = "無法載入更新紀錄";
			if (location.startsWith("http://") || location.startsWith("http://")) {
				reason = "無法取得更新項目資訊";
			}
			interruptUpdate(reason, true);
		}

		return gson.fromJson(out.toString(), JsonObject.class);
	}
	
	/**
	 * JSON 存檔程式
	 * 
	 * @param location 存檔位置
	 * @param json     JSON 值內容
	 */
	private void saveJSON(String location, JsonObject json) throws InterruptedException {
		try {
			FileUtils.write(new File(location), gson.toJson(json), "UTF-8");
		} catch(IOException ex) {
			interruptUpdate("無法儲存更新紀錄", true);
		}
	}
	
	/**
	 * 檢查檔案是否需要更新(失效)
	 * 
	 * 符合任一條件就需要更新：
	 * - 檔案不存在
	 * - 檔案沒有更新資訊
	 * - 檔案比伺服器的還舊
	 * - 遠短摘要值與本地端不同
	 * - 檔案最後 1MB 摘要值錯誤
	 * - 存放位置變更 (以後再做)
	 */
	private boolean isExpired() throws InterruptedException {
		long gzLength = currentFileInfo.get("gzLength").getAsLong();
		long exLength = currentFileInfo.get("length").getAsLong();

		// 檔案不存在檢查
		if (!currentFile.exists()) {
			trigger.onFileExpired(currentFilename, "檔案不存在", gzLength, exLength);
			return true;
		}

		// 沒有更新資訊
		if (!previousUpdateLog.getAsJsonObject("files").has(currentFilename)) {
			trigger.onFileExpired(currentFilename, "更新紀錄檔不存在", gzLength, exLength);
			return true;
		}
		
		// 檔案比伺服器舊
		JsonObject previousFileInfo = previousUpdateLog.getAsJsonObject("files").getAsJsonObject(currentFilename);
		if (previousFileInfo.get("mtime").getAsLong() < currentFileInfo.get("mtime").getAsLong()) {
			trigger.onFileExpired(currentFilename, "檔案已過時", gzLength, exLength);
			return true;
		}
		
		// 比對兩端摘要值
		String localChecksum = previousFileInfo.get("checksum").getAsString();
		String remoteChecksum = currentFileInfo.get("checksum").getAsString();
		if (!localChecksum.equals(remoteChecksum)) {
			trigger.onFileExpired(currentFilename, "檔案摘要值已變更", gzLength, exLength);
			return true;
		}
		
		// 計算最後 1MB 的 MD5 摘要
		int partNumber = (int)((currentFile.length() - 1) / partLength);
		if (!checkPart(currentFile, partNumber, localChecksum)) {
			trigger.onFileExpired(currentFilename, "檔案驗證失敗，可能有毀損", gzLength, exLength);
			return true;
		}
		
		return false;
	}

	/**
	 * 下載檔案片段
	 * 
	 * @param  partNumber 片段編號
	 * @return 是否還有下一個片段
	 */
	private boolean downloadPart(int partNumber) throws InterruptedException {
		// 計算 HTTP Range 設定值
		long offset = partNumber * partLength;
		long end    = offset + partLength - 1;
		String httpRange = String.format(Locale.getDefault(), "bytes=%d-%d", offset, end);
		
		// 配置傳輸資源
        int ptxLen = 0;
        int ftxLen = partNumber * partLength;

        try {
			// 配置 HTTP 連線
			HttpURLConnection conn = (HttpURLConnection)currentUrl.openConnection();
			conn.setRequestProperty("Range", httpRange);            // 片段下載
			conn.setRequestProperty("Accept-Encoding", "identity"); // 防止重複壓縮，這在 Android 環境是必要的
			InputStream in = conn.getInputStream();
			
			// 配置檔案系統存取
			RandomAccessFile out = new RandomAccessFile(currentGzFile, "rw"); // "r" for seek, "w" for write
	        out.seek(offset);
	        
	        do {
	        	int ioLen = in.read(BUFFER); // -1 表示讀完
	        	
	        	// 最後一個片段小於 1MB 會發生
	        	if (ioLen == -1) break;
	        	
	        	out.write(BUFFER, 0, ioLen);
	        	ftxLen += ioLen;
	        	ptxLen += ioLen;
	        	trigger.onFileTransferLength(currentFilename, ftxLen);
	        	
	        	// 取消點
	        	String progress = String.format(Locale.getDefault(), "下載 %s %d bytes 時", currentFilename, ftxLen);
	    		interruptUpdate(progress, false);
	        } while(ptxLen < partLength);
	
	        // 關閉 I/O
	        out.close();
			in.close();
        } catch(IOException ex) {
        	String reason = String.format(Locale.getDefault(), "下載片段 #%d 發生異常", partNumber);
        	trigger.onFileWarning(currentFilename, reason);
        }

		// 如果傳輸量等於 PART_SIZE 回傳 true，告知更新程式還要繼續下載
		return (ptxLen == partLength);
	}
	
	/**
	 * 解壓縮下載完成的檔案
	 */
	private void extract() throws InterruptedException {
		// 取得解壓縮後的檔案大小
		int length = currentFileInfo.get("length").getAsInt();
		
		// 解壓縮
		try {
			InputStream  in  = new GZIPInputStream(new FileInputStream(currentGzFile));
			OutputStream out = new FileOutputStream(currentFile);
			int ioLen;
			int exLen = 0;
			int prevPercent = -1;
	
			do {
				ioLen = (int)IOUtils.copyLarge(in, out, 0, BUFFER_SIZE); // 0 表示讀完
				exLen += ioLen;
				int percent = getPercent(exLen, length);
				String progress = String.format(Locale.getDefault(), "解壓縮 %s %d%% 時", currentFilename, percent);
	    		interruptUpdate(progress, false);
	        	if (percent > prevPercent) {
	        		trigger.onFileExtract(currentFilename, percent);
	        		prevPercent = percent;
	        	}
			} while(ioLen > 0);
			
			// 關閉 I/O
			out.close();
			in.close();
		} catch(IOException ex) {
			interruptUpdate("解壓縮失敗", true);
		}
	}

	/**
     * 取得本地檔案分段摘要
     *
     * @param file       本地檔案
     * @param partNumber 分段順序
     * @param expected   預期的摘要值
     * @return           是否驗證成功
     */
    private boolean checkPart(final File file, final int partNumber, final String expected) throws InterruptedException {
    	MessageDigest md = null;

    	try {
        	md = MessageDigest.getInstance(digest);
        } catch(NoSuchAlgorithmException ex) {
        	String reason = String.format(Locale.getDefault(), "無法使用 %s 演算法", digest);
        	interruptUpdate(reason, true);
        }

    	try {
	        FileInputStream   fis = new FileInputStream(file);
	        DigestInputStream dis = new DigestInputStream(fis, md);
	        
	        long off = partLength * partNumber;
	        if (dis.skip(off) < off) {
	            dis.close();
	            fis.close();
	            throw new IOException("Cannot move to offset position.");
	        }
	
	        int readlen = 1;
	        int total   = 0;
	        while (readlen > 0 && total < partLength) {
	            readlen = dis.read(BUFFER);
	            total += readlen;
	        }

	        dis.close();
	        fis.close();
    	} catch(IOException ex) {
    		String reason = String.format(Locale.getDefault(), "計算 %s 摘要失敗 (%s)", digest, ex.getMessage());
        	interruptUpdate(reason, true);
    	}

        StringBuilder sb = new StringBuilder();
        for (byte hash : md.digest()) {
            String bytestr = String.format("%02x", (hash&0xff));
            sb.append(bytestr);
        }

        String actual = sb.toString();
        sb.delete(0, sb.length());

        return actual.equals(expected);
    }

	// 計算進度值，需要防止 overflow 產生負數
	private int getPercent(int done, int total) {
		return (int)(((float)done/total)*100);
	}
	
	/**
	 * 事件產生器，轉發事件給所有 Listener
	 */
	private AutoUpdateAdapter trigger = new AutoUpdateAdapter() {
		
		private int  gzPercent;
		private long gzLength;
		
		@Override
		public void onFileBegin(String filename) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onFileBegin(filename);
			}
		}

		@Override
		public void onError(String reason) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onError(reason);
			}
		}

		@Override
		public void onFileTransferLength(String filename, long transfered) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onFileTransferLength(filename, transfered);
			}
			
			int percent = getPercent((int)transfered, (int)gzLength);
			if (percent > gzPercent) {
				for (AutoUpdateAdapter l : listenerList) {
					l.onFileTransfer(filename, percent);
				}
				gzPercent = percent;
			}
		}

		@Override
		public void onFileExtract(String filename, int percent) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onFileExtract(filename, percent);
			}
		}

		@Override
		public void onFileComplete(String filename, boolean isNew) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onFileComplete(filename, isNew);
			}
		}

		@Override
		public void onComplete() {
			for (AutoUpdateAdapter l : listenerList) {
				l.onComplete();
			}
		}
		
		@Override
		public void onFileWarning(String filename, String reason) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onFileWarning(filename, reason);
			}
		}

		@Override
		public void onFileExpired(String filename, String reason, long gzLength, long exLength) {
			this.gzLength  = gzLength;
			this.gzPercent = -1;
			for (AutoUpdateAdapter l : listenerList) {
				l.onFileExpired(filename, reason, gzLength, exLength);
			}
		}

		@Override
		public void onUserCancel(String progress) {
			for (AutoUpdateAdapter l : listenerList) {
				l.onUserCancel(progress);
			}
		}
		
	};
	
}
