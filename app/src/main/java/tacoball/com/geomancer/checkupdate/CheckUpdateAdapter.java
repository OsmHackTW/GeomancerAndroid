package tacoball.com.geomancer.checkupdate;

public class CheckUpdateAdapter {

	/**
	 * 檢查完成
	 * 
	 * @param totalLength  需要更新的總長度
	 * @param lastModified 相關檔案最後更新時間
	 */
	public void onCheck(long totalLength, String lastModified) {
		System.out.printf("需要更新 %d bytes, 異動日期: %s\n", totalLength, lastModified);
	}
	
	/**
	 * 檢查更新時發生錯誤
	 * 
	 * @param reason 錯誤原因
	 */
	public void onError(String reason) {
		System.err.println(reason);
	}
	
}
