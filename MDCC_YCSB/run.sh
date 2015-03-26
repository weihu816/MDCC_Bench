echo running [threads: $1] workloads: [$2_workload_$3]
./bin/ycsb run mdcc -threads $1 -P workloads/$2_workload_$3 -p backOffBase=0 -p backOffTime=300 -s > run.data