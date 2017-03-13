package tacoball.com.geomancer.checkupdate;

/**
 * 自動更新進度接收程式
 */
public class AutoUpdateAdapter {

	/**
	 * 回報檔案需要更新
	 * 
	 * @param filename 更新中檔案
	 * @param reason   需要更新的原因
	 * @param gzLength 更新檔壓縮長度
	 * @param exLength 更新檔原始長度
	 */
	public void onFileExpired(String filename, String reason, long gzLength, long exLength) {}
	
	/**
	 * 回報開始更新檔案
	 * 
	 * @param filename 更新中檔案
	 */
	public void onFileBegin(String filename) {}

	/**
	 * 回報傳輸進度，每填滿一次緩衝區觸發一次
	 * 
	 * @param filename   更新中檔案
	 * @param transfered 已傳輸長度
	 */
	public void onFileTransferLength(String filename, long transfered) {}
	
	/**
	 * 回報傳輸進度，進度百分比異動時觸發一次
	 * 
	 * @param filename 更新中檔案
	 * @param percent   進度百分比
	 */
	public void onFileTransfer(String filename, int percent) {}
	
	/**
	 * 回報解壓縮進度
	 * 
	 * @param filename  更新中檔案
	 * @param percent   進度百分比
	 */
	public void onFileExtract(String filename, int percent) {}
	
	/**
	 * 回報完成一個檔案
	 * 
	 * @param filename 更新中檔案
	 * @param isNew    是否有更新
	 */
	public void onFileComplete(String filename, boolean isNew) {}
	
	/**
	 * 回報一項警告，此時更新程序會自動修復
	 * 
	 * @param filename 更新中檔案
	 * @param reason   原因
	 */
	public void onFileWarning(String filename, String reason) {}
	
	/**
	 * 回報更新完成
	 */
	public void onComplete() {}
	
	/**
	 * 回報更新失敗，發生後會結束更新作業
	 * 
	 * @param reason 原因
	 */
	public void onError(String reason) {}
	
	/**
	 * 回報更新取消，發生後會結束更新作業
	 */
	public void onUserCancel(String progress) {}
	
}
