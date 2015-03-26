Yahoo! Cloud System Benchmark (YCSB) + MDCC
====================================

Running YCSB on MDCC Protocol
---------------
Assume the number of Noded is 3.
The steps to run MDCC Prototype on Node1, Node2 and Node3 are as follows.

1. Download
---
For each node,

	$ cd ~
	$ git clone https://github.com/weihu816/YCSB

2. Modify configuration
---
For each node, modify the configuration information

	$ cd YCSB

	zk.properties:
		For example:
			server.0=[????]:10110:20110
			server.1=[????]:10110:20110
			server.2=[????]:10110:20110
	mdcc.properties:
			mdcc.server.0=[????]:9090
			mdcc.server.1=[????]:9090
			mdcc.server.2=[????]:9090
			mdcc.my.id=[0 or 1 or 2]
	app-server.properties:
			server.0=[????]:10110:20110
			server.1=[????]:10110:20110
			server.2=[????]:10110:20110
			mdcc.app.server=[??????]:9190 
				(app-server can be started in one of the nodes)

3. Run StorageNode on each node
---
For each node,
	
	$ cd ~/YCSB/mdcc/pom.xml
		modify the following line
		<systemPath>??????/YCSB/mdcc/src/main/conf/mdcc-core-1.0.jar</systemPath>
	$mvn package

	$ java -classpath ~/YCSB/mdcc/src/main/conf/mdcc-core-1.0.jar:lib/* edu.ucsb.cs.mdcc.paxos.StorageNode

	(Ooh, ZooKeeper will kepp showing Exceptions. That's fine. As long as all of the nodes are started, it will be stable. Chenk this.)

3. Run AppServer
---
	$ java -classpath ~/YCSB/mdcc/src/main/conf/mdcc-core-1.0.jar:lib/* edu.ucsb.cs.mdcc.paxos.AppServer

4. Run YCSB
---
load data

$ 
./bin/ycsb load  mdcc -p workload=com.yahoo.ycsb.workloads.CoreWorkload -p recordcount=10000 -p readallfields=false -p fieldlength=10 -p backOffBase=0 -p backOffTime=300 -s > transaction.data


First Experiment:
$
./bin/ycsb run mdcc -threads 1 -p fieldlength=10 -P workloads/1_workload_1 -p backOffBase=0 -p backOffTime=300 -s > log.data
Second Experiment:
$ bin/ycsb run mdcc -threads 20 -p readallfields=false -P [workloads/?????] -s

