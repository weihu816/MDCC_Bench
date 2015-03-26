cp conf/mdcc2.properties conf/mdcc.properties
cp conf/zk2.properties conf/zk.properties
java -classpath ~/YCSB/mdcc/src/main/conf/mdcc-core-1.0.jar:lib/* edu.ucsb.cs.mdcc.paxos.StorageNode
