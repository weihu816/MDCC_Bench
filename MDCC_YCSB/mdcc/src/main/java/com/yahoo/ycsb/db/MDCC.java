package com.yahoo.ycsb.db;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.Client;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.RandomByteIterator;
import com.yahoo.ycsb.StringByteIterator;
import com.yahoo.ycsb.Utils;
import com.yahoo.ycsb.generator.ConstantIntegerGenerator;
import com.yahoo.ycsb.generator.CounterGenerator;
import com.yahoo.ycsb.generator.DiscreteGenerator;
import com.yahoo.ycsb.generator.ExponentialGenerator;
import com.yahoo.ycsb.generator.Generator;
import com.yahoo.ycsb.generator.HistogramGenerator;
import com.yahoo.ycsb.generator.HotspotIntegerGenerator;
import com.yahoo.ycsb.generator.IntegerGenerator;
import com.yahoo.ycsb.generator.ScrambledZipfianGenerator;
import com.yahoo.ycsb.generator.SkewedLatestGenerator;
import com.yahoo.ycsb.generator.UniformIntegerGenerator;
import com.yahoo.ycsb.generator.ZipfianGenerator;
import com.yahoo.ycsb.workloads.CoreWorkload;

import edu.ucsb.cs.mdcc.paxos.Transaction;
import edu.ucsb.cs.mdcc.paxos.TransactionException;
import edu.ucsb.cs.mdcc.txn.TransactionFactory;

public class MDCC extends DB {

	public int backoffT, backoffB;
	
	public static final int RETRYTIMES = 0;

	final TransactionFactory fac = new TransactionFactory();
;
	
	@Override
	public void init()
	{
		
		this.backoffB = Integer.parseInt((String) this.getProperties().get("backOffBase"));
		this.backoffT = Integer.parseInt((String) this.getProperties().get("backOffTime"));
		
		coreworkloadinit();
	}
	
	private Random rand = new Random(System.nanoTime());
	private void randomBackoff(){
		try {
			long sleepTime = (long) (rand.nextInt(10000) % this.backoffT + this.backoffB);
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public int delete(String tableName, String key) {
		while(true){
			Transaction t = fac.create();
			t.begin();
			try {
				t.delete(key);
				t.commit();
			} catch (TransactionException e) {
				randomBackoff();
				continue;
			}
			break;
		}
		return 0;
	}

	@Override
	public int insert(String tableName, String key, HashMap<String, ByteIterator> values) {

		while (true) {
			boolean toCon = false;
			Transaction t = fac.create();
			t.begin();
			try {
				for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
					StringBuilder str = new StringBuilder(tableName);
					str.append("_");
					str.append(key);
					str.append("_");
					str.append(entry.getKey());
					t.write(str.toString(), entry.getValue().toString().getBytes());
				}
			} catch (TransactionException e) {
				toCon = true;
			}

			if (toCon) {
				randomBackoff();
				continue;
			}

			try {
				t.commit();
			} catch (TransactionException e) {
				randomBackoff();
				continue;
			}
			break;
		}
		return 0;
	}

	@Override
	public int read(String tableName, String key, Set<String> fields,
			HashMap<String, ByteIterator> result) {
		while (true) {
			boolean toCon = false;
			Transaction t = fac.create();
			t.begin();
			int opcnt = 0;
			String lkey = key;
			do {
				try {
					for (String field : fields) {
						StringBuilder str = new StringBuilder(tableName);
						str.append("_");
						str.append(lkey);
						str.append("_");
						str.append(field);
						byte[] x = t.read(str.toString());
						if (x == null) {
							toCon = true;
							break;
						}
						result.put(field, new StringByteIterator(x.toString()));
					}
				} catch (TransactionException e) {
					toCon = true;
					break;
				}
				lkey = buildKeyName(nextKeynum());
				opcnt++;
			} while (opcnt < txnlength);

			if (toCon) {
				randomBackoff();
				continue;
			}

			try {
				t.commit();
			} catch (TransactionException e) {
				randomBackoff();
				continue;
			}
			break;
		}
		return 0;
	}

	@Override
	public int scan(String table, String startkey, int recordcount, Set<String> fields,
			Vector<HashMap<String, ByteIterator>> result) {	
		return 0;
	}

	@Override
	public int update(String tableName, String key, HashMap<String, ByteIterator> values) {
		HashMap<String,ByteIterator> lvals;
		while (true) {
			boolean toCon = false;
			int opcnt = 0;
			String lkey = key;
			lvals = values;
			Transaction t = fac.create();
			t.begin();
			do {				
				try {
					for (Map.Entry<String, ByteIterator> entry : lvals.entrySet()) {
						StringBuilder str = new StringBuilder(tableName);
						str.append("_");
						str.append(lkey);
						str.append("_");
						str.append(entry.getKey());
						t.write(str.toString(), entry.getValue().toString().getBytes());
					}
					
				} catch (TransactionException e) {
					toCon = true;
					break;
				}
				
				lkey =buildKeyName(nextKeynum());
				if (writeallfields) {
					lvals = buildValues(); 	// new data for all the fields

				} else { 
					lvals = buildUpdate(); // update a random field
				}
				opcnt++;
			}while(opcnt<txnlength);

			if(toCon){ 
				randomBackoff(); 
				continue;
			}
			
			try {
				t.commit();
			} catch (TransactionException e) { 
				randomBackoff();
				continue; 
			}

			break;
		}
		return 0;
	}
	
	// /////////////////////////////////////////////////

	int fieldcount;
	IntegerGenerator fieldlengthgenerator;
	boolean readallfields;
	boolean writeallfields;

	IntegerGenerator keysequence;

	DiscreteGenerator operationchooser;

	IntegerGenerator keychooser;

	Generator fieldchooser;

	CounterGenerator transactioninsertkeysequence;

	IntegerGenerator scanlength;

	boolean orderedinserts;

	int recordcount;

	public String buildKeyName(long keynum) {
 		if (!orderedinserts)
 		{
 			keynum=Utils.hash(keynum);
 		}
		return "user"+keynum;
	}
	HashMap<String, ByteIterator> buildValues() {
 		HashMap<String,ByteIterator> values=new HashMap<String,ByteIterator>();

 		for (int i=0; i<fieldcount; i++)
 		{
 			String fieldkey="field"+i;
 			ByteIterator data= new RandomByteIterator(fieldlengthgenerator.nextInt());
 			values.put(fieldkey,data);
 		}
		return values;
	}
	
	HashMap<String, ByteIterator> buildUpdate() {
		//update a random field
		HashMap<String, ByteIterator> values=new HashMap<String,ByteIterator>();
		String fieldname="field"+fieldchooser.nextString();
		ByteIterator data = new RandomByteIterator(fieldlengthgenerator.nextInt());
		values.put(fieldname,data);
		return values;
	}
	
	int nextKeynum() {
        int keynum;
        if(keychooser instanceof ExponentialGenerator) {
            do
                {
                    keynum=transactioninsertkeysequence.lastInt() - keychooser.nextInt();
                }
            while(keynum < 0);
        } else {
            do
                {
                    keynum=keychooser.nextInt();
                }
            while (keynum > transactioninsertkeysequence.lastInt());
        }
        return keynum;
    }

	
	int txnlength;
    boolean cachewrites;
    public static final String TRANSACTION_LENGTH = "txnlength";
    public static final String TRANSACTION_LENGTH_DEFAULT = "1";
    public static final String CACHED_WRITES = "cachewrites";
    public static final String CACHED_WRITES_DEFAULT = "true";
    
	void coreworkloadinit(){
    	Properties p = this.getProperties();
		fieldcount=Integer.parseInt(p.getProperty(CoreWorkload.FIELD_COUNT_PROPERTY,CoreWorkload.FIELD_COUNT_PROPERTY_DEFAULT));
		fieldlengthgenerator = getFieldLengthGenerator(p);
		
		txnlength=Integer.parseInt(p.getProperty(TRANSACTION_LENGTH, TRANSACTION_LENGTH_DEFAULT));
		cachewrites=Boolean.valueOf(p.getProperty(CACHED_WRITES, CACHED_WRITES_DEFAULT));
		
		double readproportion=Double.parseDouble(p.getProperty(CoreWorkload.READ_PROPORTION_PROPERTY,CoreWorkload.READ_PROPORTION_PROPERTY_DEFAULT));
		double updateproportion=Double.parseDouble(p.getProperty(CoreWorkload.UPDATE_PROPORTION_PROPERTY,CoreWorkload.UPDATE_PROPORTION_PROPERTY_DEFAULT));
		double insertproportion=Double.parseDouble(p.getProperty(CoreWorkload.INSERT_PROPORTION_PROPERTY,CoreWorkload.INSERT_PROPORTION_PROPERTY_DEFAULT));
		double scanproportion=Double.parseDouble(p.getProperty(CoreWorkload.SCAN_PROPORTION_PROPERTY,CoreWorkload.SCAN_PROPORTION_PROPERTY_DEFAULT));
		double readmodifywriteproportion=Double.parseDouble(p.getProperty(CoreWorkload.READMODIFYWRITE_PROPORTION_PROPERTY,CoreWorkload.READMODIFYWRITE_PROPORTION_PROPERTY_DEFAULT));
		recordcount=Integer.parseInt(p.getProperty(Client.RECORD_COUNT_PROPERTY));
		String requestdistrib=p.getProperty(CoreWorkload.REQUEST_DISTRIBUTION_PROPERTY,CoreWorkload.REQUEST_DISTRIBUTION_PROPERTY_DEFAULT);
		int maxscanlength=Integer.parseInt(p.getProperty(CoreWorkload.MAX_SCAN_LENGTH_PROPERTY,CoreWorkload.MAX_SCAN_LENGTH_PROPERTY_DEFAULT));
		String scanlengthdistrib=p.getProperty(CoreWorkload.SCAN_LENGTH_DISTRIBUTION_PROPERTY,CoreWorkload.SCAN_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);
		
		int insertstart=Integer.parseInt(p.getProperty(CoreWorkload.INSERT_START_PROPERTY,CoreWorkload.INSERT_START_PROPERTY_DEFAULT));
		
		readallfields=Boolean.parseBoolean(p.getProperty(CoreWorkload.READ_ALL_FIELDS_PROPERTY,CoreWorkload.READ_ALL_FIELDS_PROPERTY_DEFAULT));
		writeallfields=Boolean.parseBoolean(p.getProperty(CoreWorkload.WRITE_ALL_FIELDS_PROPERTY,CoreWorkload.WRITE_ALL_FIELDS_PROPERTY_DEFAULT));
		
		if (p.getProperty(CoreWorkload.INSERT_ORDER_PROPERTY,CoreWorkload.INSERT_ORDER_PROPERTY_DEFAULT).compareTo("hashed")==0)
		{
			orderedinserts=false;
		}
		else if (requestdistrib.compareTo("exponential")==0)
		{
                    double percentile = Double.parseDouble(p.getProperty(ExponentialGenerator.EXPONENTIAL_PERCENTILE_PROPERTY,
                                                                         ExponentialGenerator.EXPONENTIAL_PERCENTILE_DEFAULT));
                    double frac       = Double.parseDouble(p.getProperty(ExponentialGenerator.EXPONENTIAL_FRAC_PROPERTY,
                                                                         ExponentialGenerator.EXPONENTIAL_FRAC_DEFAULT));
                    keychooser = new ExponentialGenerator(percentile, recordcount*frac);
		}
		else
		{
			orderedinserts=true;
		}

		keysequence=new CounterGenerator(insertstart);
		operationchooser=new DiscreteGenerator();
		if (readproportion>0)
		{
			operationchooser.addValue(readproportion,"READ");
		}

		if (updateproportion>0)
		{
			operationchooser.addValue(updateproportion,"UPDATE");
		}

		if (insertproportion>0)
		{
			operationchooser.addValue(insertproportion,"INSERT");
		}
		
		if (scanproportion>0)
		{
			operationchooser.addValue(scanproportion,"SCAN");
		}
		
		if (readmodifywriteproportion>0)
		{
			operationchooser.addValue(readmodifywriteproportion,"READMODIFYWRITE");
		}

		transactioninsertkeysequence=new CounterGenerator(recordcount);
		if (requestdistrib.compareTo("uniform")==0)
		{
			keychooser=new UniformIntegerGenerator(0,recordcount-1);
		}
		else if (requestdistrib.compareTo("zipfian")==0)
		{
			//it does this by generating a random "next key" in part by taking the modulus over the number of keys
			//if the number of keys changes, this would shift the modulus, and we don't want that to change which keys are popular
			//so we'll actually construct the scrambled zipfian generator with a keyspace that is larger than exists at the beginning
			//of the test. that is, we'll predict the number of inserts, and tell the scrambled zipfian generator the number of existing keys
			//plus the number of predicted keys as the total keyspace. then, if the generator picks a key that hasn't been inserted yet, will
			//just ignore it and pick another key. this way, the size of the keyspace doesn't change from the perspective of the scrambled zipfian generator
			
			int opcount=Integer.parseInt(p.getProperty(Client.OPERATION_COUNT_PROPERTY));
			int expectednewkeys=(int)(((double)opcount)*insertproportion*2.0); //2 is fudge factor
			
			keychooser=new ScrambledZipfianGenerator(recordcount+expectednewkeys);
		}
		else if (requestdistrib.compareTo("latest")==0)
		{
			keychooser=new SkewedLatestGenerator(transactioninsertkeysequence);
		}
		else if (requestdistrib.equals("hotspot")) 
		{
      double hotsetfraction = Double.parseDouble(p.getProperty(
    		  CoreWorkload.HOTSPOT_DATA_FRACTION, CoreWorkload.HOTSPOT_DATA_FRACTION_DEFAULT));
      double hotopnfraction = Double.parseDouble(p.getProperty(
    		  CoreWorkload.HOTSPOT_OPN_FRACTION, CoreWorkload.HOTSPOT_OPN_FRACTION_DEFAULT));
      keychooser = new HotspotIntegerGenerator(0, recordcount - 1, 
          hotsetfraction, hotopnfraction);
    }
		else
		{
			System.err.println("Unknown request distribution \""+requestdistrib+"\"");
		}

		fieldchooser=new UniformIntegerGenerator(0,fieldcount-1);
		
		if (scanlengthdistrib.compareTo("uniform")==0)
		{
			scanlength=new UniformIntegerGenerator(1,maxscanlength);
		}
		else if (scanlengthdistrib.compareTo("zipfian")==0)
		{
			scanlength=new ZipfianGenerator(1,maxscanlength);
		}
		else
		{
			System.err.println("Distribution \""+scanlengthdistrib+"\" not allowed for scan length");
		}
    }
	
	static IntegerGenerator getFieldLengthGenerator(Properties p){
		IntegerGenerator fieldlengthgenerator;
		String fieldlengthdistribution = p.getProperty(CoreWorkload.FIELD_LENGTH_DISTRIBUTION_PROPERTY, CoreWorkload.FIELD_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);
		int fieldlength=Integer.parseInt(p.getProperty(CoreWorkload.FIELD_LENGTH_PROPERTY,CoreWorkload.FIELD_LENGTH_PROPERTY_DEFAULT));
		String fieldlengthhistogram = p.getProperty(CoreWorkload.FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY, CoreWorkload.FIELD_LENGTH_HISTOGRAM_FILE_PROPERTY_DEFAULT);
		if(fieldlengthdistribution.compareTo("constant") == 0) {
			fieldlengthgenerator = new ConstantIntegerGenerator(fieldlength);
		} else if(fieldlengthdistribution.compareTo("uniform") == 0) {
			fieldlengthgenerator = new UniformIntegerGenerator(1, fieldlength);
		} else if(fieldlengthdistribution.compareTo("zipfian") == 0) {
			fieldlengthgenerator = new ZipfianGenerator(1, fieldlength);
		} else if(fieldlengthdistribution.compareTo("histogram") == 0) {
			try {
				fieldlengthgenerator = new HistogramGenerator(fieldlengthhistogram);
			} catch(IOException e) {
				System.err.println("Couldn't read field length histogram file: "+fieldlengthhistogram+"\n"+e);
				fieldlengthgenerator = new UniformIntegerGenerator(1, fieldlength);
			}
		} else {
			System.err.println("Unknown field length distribution \""+fieldlengthdistribution+"\"");
			fieldlengthgenerator = new UniformIntegerGenerator(1, fieldlength);
		}
		return fieldlengthgenerator;
	}
}
