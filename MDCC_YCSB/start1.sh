cp conf/mdcc1.properties conf/mdcc.properties
cp conf/zk1.properties conf/zk.properties
java -classpath ~/YCSB/mdcc/src/main/conf/mdcc-core-1.0.jar:lib/* edu.ucsb.cs.mdcc.paxos.StorageNode
