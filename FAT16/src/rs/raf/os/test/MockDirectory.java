package rs.raf.os.test;

import java.util.HashMap;
import java.util.Map;

import rs.raf.os.dir.Directory;
import rs.raf.os.dir.DirectoryException;
import rs.raf.os.disk.Disk;
import rs.raf.os.fat.FAT16;

public class MockDirectory implements Directory {
	
	private Map<String, Integer> mapaImePocetniKlaster;
	private Map<String, Integer> mapaImeDuzinaFajla;
	private Map<String, Integer> mapaImeUkupanBrojKlastera;	//za delete metodu
	
	FAT16 fat;
	Disk disk;
	
	//za getUsableTotalSpace & getUsableFreeSpace metode
	private int ukupniProstorFAT = 0;
	private int ukupniProstorDISK = 0;
	private int ukupanSlobodanProstor = 0;
	int DATALENGTH = 0;
	

	public MockDirectory(FAT16 fat, Disk disk) {
		this.fat = fat;
		this.disk = disk;
		mapaImePocetniKlaster = new HashMap<String, Integer>();
		mapaImeDuzinaFajla = new HashMap<String, Integer>();
		mapaImeUkupanBrojKlastera = new HashMap<String, Integer>();
	}
	
	@Override
	public boolean writeFile(String name, byte[] data) {
		//provera dal postoji vec taj fajl i ako da, obrisati ga
		if(mapaImePocetniKlaster.containsKey(name)) {
			this.deleteFile(name);	
		}
		//provera slobodnog prostora
		DATALENGTH = data.length;
		if(data.length>ukupanSlobodanProstor)
			return false;
		
		ukupanSlobodanProstor -= data.length + (data.length % disk.getSectorSize());
		
		//trazenje prvog slobodnog klastera
		int pocetniKlaster = 2;
		for(int i=2; i<fat.getClusterCount()+2; i++) {
			if(fat.readCluster(i)==0) {
				pocetniKlaster = i;
				mapaImePocetniKlaster.put(name, pocetniKlaster);
				break;
			}
		}
		
		//koliko klastera treba za fajl
		int brojPotrebnihKlastera = 0;
		int tmpDataSize = 0;
		while(tmpDataSize<data.length) {
			tmpDataSize += fat.getClusterWidth()*disk.getSectorSize();
			brojPotrebnihKlastera++;
		}
		mapaImeUkupanBrojKlastera.put(name, brojPotrebnihKlastera);	//za delete metodu
		
		//nadji brojPotrebnihKlastera da su slobodni i ubaci u niz
		int[] slobodniKlasteri = new int[brojPotrebnihKlastera];
		int brojac = 0;
		for(int i=pocetniKlaster; i<fat.getClusterCount()+2; i++) {
			if(brojac==brojPotrebnihKlastera)	//niz slobodniKlasteri je napunjen
				break;
			if(fat.readCluster(i)==0) {
				slobodniKlasteri[brojac]=i;
				brojac++;
			}
		}
		
		//upisi u klastere
		for(int i=0; i<slobodniKlasteri.length; i++) {
			if(i==slobodniKlasteri.length-1) 
				fat.writeCluster(slobodniKlasteri[i], fat.getEndOfChain());
			else 
				fat.writeCluster(slobodniKlasteri[i], slobodniKlasteri[i+1]);
		}
		//FAT TABELA ZAVRSENA
		
		//UPIS NA DISK
		//Iseckam data na parcice od po velicine sektora i upisujem
		mapaImeDuzinaFajla.put(name, data.length);
		/*byte[] dataNiz = new byte[data.length]; //byte[] dataNiz = data;
		dataNiz = data;0
		int dataBrojac = 0;
		int zaGranicu = 0;
		byte[] nizZaUpis = new byte[disk.getSectorSize()];
		int nizZaUpisBrojac = 0;
		boolean stop = false;
		int trenutniKlaster = mapaImePocetniKlaster.get(name);
		int trenutniSadrzajKlastera = fat.readCluster(trenutniKlaster);
		do {
			int sektorZaUpis = (trenutniKlaster-2)*fat.getClusterWidth();
			for(int i=0; i<fat.getClusterWidth(); i++) {
				if(stop)	//ako ne treba da ispise ceo sektor
					break;
				else {
					zaGranicu = dataBrojac;
					for(int j=dataBrojac; j<disk.getSectorSize()+zaGranicu; j++) {	//iseckaj dataNiz
						if(dataBrojac>=data.length) {	//ako je dosao do kraja data
							stop = true;
							/*for(int k = nizZaUpisBrojac; k<nizZaUpis.length; k++){	//da se nizZaUpis popuni nulama od trenutka zavrsetka data.length
								nizZaUpis[k]=0;
							}*//*
							break;
						}
						nizZaUpis[nizZaUpisBrojac] = dataNiz[dataBrojac];
						nizZaUpisBrojac++;
						dataBrojac++;
					}
					nizZaUpisBrojac = 0;
					disk.writeSector(sektorZaUpis, nizZaUpis);
					sektorZaUpis++;
				}	
			}
			
			trenutniSadrzajKlastera = fat.readCluster(trenutniKlaster);
			trenutniKlaster = fat.readCluster(trenutniKlaster);
			
		} while(trenutniSadrzajKlastera!=fat.getEndOfChain());*/
		
		byte[] dataNiz = data;
		byte[] nizZaUpis = new byte[brojPotrebnihKlastera*fat.getClusterWidth()*disk.getSectorSize()];
		for(int i=0; i<brojPotrebnihKlastera*fat.getClusterWidth()*disk.getSectorSize(); i++) {
			if(i>=data.length) {
				nizZaUpis[i] = 0;
			} else {
				nizZaUpis[i]=dataNiz[i];
			}
		}
		disk.writeSectors((pocetniKlaster-2)*fat.getClusterWidth(), brojPotrebnihKlastera*fat.getClusterWidth(), nizZaUpis);
		
		//this.getUsableFreeSpace(); ne moze jer gleda koji je manji fat ili disk prvo
		return true;
	}

	@Override
	public byte[] readFile(String name) throws DirectoryException {
		if(!mapaImeDuzinaFajla.containsKey(name))
			throw new DirectoryException("Fajl sa imenom: '"+name+"' ne postoji.");
		int duzinaFajla = mapaImeDuzinaFajla.get(name);
		byte[] dataNiz = new byte[duzinaFajla];
		int dataBrojac = 0;
		int zaGranicu = 0;
		byte[] procitanSektorNiz = new byte[disk.getSectorSize()];
		int procitanSektorNizBrojac = 0;
		boolean stop = false;
		int trenutniKlaster = mapaImePocetniKlaster.get(name);
		int trenutniSadrzajKlastera = fat.readCluster(trenutniKlaster);
		do {
			int sektorZaCitanje = (trenutniKlaster-2)*fat.getClusterWidth();
			for(int i=0; i<fat.getClusterWidth(); i++) {
				if(stop)	//ako ne treba da procita ceo sektor
					break;
				else {
					procitanSektorNiz=disk.readSector(sektorZaCitanje);
					sektorZaCitanje++;
					
					zaGranicu=dataBrojac;
					for(int j=dataBrojac; j<disk.getSectorSize()+zaGranicu; j++) {	//prebaci procitanSektorNiz u dataNiz
						if(dataBrojac>=duzinaFajla) {
							stop = true;
							break;
						}
						dataNiz[dataBrojac]=procitanSektorNiz[procitanSektorNizBrojac];
						dataBrojac++;
						procitanSektorNizBrojac++;
					}
					procitanSektorNizBrojac=0;
				}
			}
			
			
			trenutniSadrzajKlastera = fat.readCluster(trenutniKlaster);
			trenutniKlaster = fat.readCluster(trenutniKlaster);
			
		} while(trenutniSadrzajKlastera!=fat.getEndOfChain());
		
		
		return dataNiz;
	}

	@Override
	public void deleteFile(String name) throws DirectoryException {
		if(!mapaImePocetniKlaster.containsKey(name))
			throw new DirectoryException("Fajl sa imenom: '"+name+"' ne postoji.");
		int[] klasteriZaBrisanje = new int[mapaImeUkupanBrojKlastera.get(name)];
		int i = 0;
		int trenutniKlaster = mapaImePocetniKlaster.get(name);
		int trenutniSadrzajKlastera = fat.readCluster(trenutniKlaster);
		do {
			klasteriZaBrisanje[i++]=trenutniKlaster;
			
			trenutniKlaster = fat.readCluster(trenutniKlaster);
			trenutniSadrzajKlastera = fat.readCluster(trenutniKlaster);
		} while(trenutniSadrzajKlastera!=fat.getEndOfChain());
		klasteriZaBrisanje[i]=trenutniKlaster;
		
		for(int j=0; j<klasteriZaBrisanje.length; j++) {
			fat.writeCluster(klasteriZaBrisanje[j], 0);
		}
		
		//oslobodi ukupanSlobodniProstor i izbrisi iz mapa
		ukupanSlobodanProstor += mapaImeUkupanBrojKlastera.get(name)*disk.getSectorSize()*fat.getClusterWidth();
		//ukupanSlobodanProstor += mapaImeDuzinaFajla.get(name) + (mapaImeDuzinaFajla.get(name) % disk.getSectorSize());
		mapaImePocetniKlaster.remove(name);
		mapaImeDuzinaFajla.remove(name);
		mapaImeUkupanBrojKlastera.remove(name);
		
	}

	@Override
	public String[] listFiles() {
		return null;
	}

	@Override
	public int getUsableTotalSpace() {
		//FAT provera
		//ukupniProstorFAT = brojKlastera*sirinaKlastera*velicinaSektora;
		ukupniProstorFAT = fat.getClusterCount()*fat.getClusterWidth()*disk.getSectorSize();
		
		//DISK provera
		//ukupniProstorDISK = velicinaSektora*brojSektora;
		ukupniProstorDISK = disk.getSectorSize()*disk.getSectorCount();
		
		//PROVERA KOJI JE MANJI
		if(ukupniProstorFAT < ukupniProstorDISK)
			return ukupniProstorFAT;
		else
			return ukupniProstorDISK;
		
		
	}

	@Override
	public int getUsableFreeSpace() {
		//PROVERA KOJI JE MANJI
		//FAT provera
		ukupniProstorFAT = fat.getClusterCount()*fat.getClusterWidth()*disk.getSectorSize();
		//DISK provera
		ukupniProstorDISK = disk.getSectorSize()*disk.getSectorCount();
		
		int brojSlobodnihKlastera=0;
		//Ako je FAT tabela manja
		if(ukupniProstorFAT < ukupniProstorDISK) {
			//prodjem sve klastere i svi koji su 0 su slobodni
			for(int i=2; i<fat.getClusterCount()+2; i++) {	//krecem od drugog klastera jer sam tako napravio u FAT klasi
				if(fat.readCluster(i)==0)
					brojSlobodnihKlastera++;
			}
			ukupanSlobodanProstor = brojSlobodnihKlastera*fat.getClusterWidth()*disk.getSectorSize();
			return ukupanSlobodanProstor;
		} else {	//Ako je DISK manji
			int brojKlasteraNaOsnovuDiska = ukupniProstorDISK/disk.getSectorSize()/fat.getClusterWidth();
			for(int i=2; i<brojKlasteraNaOsnovuDiska+2; i++) {	//krecem od drugog klastera jer sam tako napravio u FAT klasi
				if(fat.readCluster(i)==0)
					brojSlobodnihKlastera++;
			}
			ukupanSlobodanProstor = brojSlobodnihKlastera*fat.getClusterWidth()*disk.getSectorSize();
			return ukupanSlobodanProstor;
		}
		
	}

}
