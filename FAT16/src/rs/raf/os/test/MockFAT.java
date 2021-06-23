package rs.raf.os.test;

import rs.raf.os.fat.FAT16;
import rs.raf.os.fat.FATException;

public class MockFAT implements FAT16 {
	
	private int clusterWidth = 1; //po defaultu
	private int clusterCount = 0xFFEF-2; //0xFFEF - 2 po defaultu	//65528 stoji u testu?
	private int[] fatTable;

	public MockFAT(int clusterWidth) {
		this.clusterWidth = clusterWidth;
		fatTable = new int[clusterCount+2];
	}
	
	public MockFAT(int clusterWidth, int clusterCount) {
		if(clusterCount > 65517) { //i clusterCount<1
			this.clusterCount = 65517;
		} else {
			this.clusterCount = clusterCount;
		}
		this.clusterWidth = clusterWidth;
		fatTable = new int[this.clusterCount+2];
	}
	
	@Override
	public int getEndOfChain() {
		return 65528;  //0xFFF8
	}

	@Override
	public int getClusterCount() {
		return clusterCount;
	}

	@Override
	public int getClusterWidth() {
		return clusterWidth;
	}

	@Override
	public int readCluster(int clusterID) throws FATException {
		if(clusterID<2 || clusterID>=clusterCount+2)
			throw new FATException("Citanje iz klastera koji je rezervisan ili ne postoji.");
		else 
			return fatTable[clusterID];
	}

	@Override
	public void writeCluster(int clusterID, int valueToWrite) throws FATException {
		if(clusterID<2 || clusterID>=clusterCount+2)
			throw new FATException("Upisivanje u klaster koji je rezervisan ili ne postoji.");
		else 
			fatTable[clusterID]=valueToWrite;
	}

	@Override
	public String getString() {
		String fatTabelaUStringu = "[";
		for(int i = 2; i<clusterCount+2; i++) {
			fatTabelaUStringu += Integer.toString(fatTable[i]);
			if(i!=clusterCount+1){	//dodaje "|" sve do nakon poslednjeg broja
				fatTabelaUStringu += "|";
			}
		}
		fatTabelaUStringu += "]";
				
		return fatTabelaUStringu;
	}

}
