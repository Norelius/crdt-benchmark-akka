#!/bin/bash
set -e

# Takes a benchmark logs and extracts the start and finish time for each run of the experiment.

process(){
	local FILE=$1
	
	local REPLICAS=$2

	local CLIENTS=$3

	local RUNS=$4

	sed -n '2p' "${FILE}"  | sed 's/^/# /'
	echo "# Start 	Finish"


	for i in $(eval echo {1..$RUNS})
	do
		grep -ohm$((($i-1)*$CLIENTS+1)) 'Start time: [0-9]*' $1 | tail -n 1 | tr -dc [:digit:] 
		grep -ohm$(($i*$REPLICAS)) 'Finish time: [0-9]*' $1 | tail -n 1 | tr -c [:digit:] ' ' | tr -s "[:blank:]"
		echo 
	done
}


main(){
	local FILE=$1

	if [[ ! -f "$FILE" ]]; then
		echo "Can't find file: ${FILE}"
		exit
	fi

	local REPLICAS=$(eval sed -n '2p' "${FILE}" | tr ',' '\n' | sed -n '4p' | tr -dc [:digit:])

	local CLIENTS=$(eval sed -n '2p' "${FILE}" | tr ',' '\n' | sed -n '5p' | tr -dc [:digit:])

	local RUNS=$(eval sed -n '2p' "${FILE}" | tr ',' '\n' | sed -n '3p' | tr -dc [:digit:])

	process "${FILE}" "${REPLICAS}" "${CLIENTS}" "${RUNS}"
}

main $*