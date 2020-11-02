#!/bin/bash
set -e

# Takes a benchmark logs and extracts the start and finish time for each run of the experiment.

process(){
	local FILE=$1
	
	sed -n '2p' "${FILE}" | tr ',' '\n' | sed -n '3p' | tr -dc [:digit:]'\n' # number of runs
	sed -n '2p' "${FILE}" | tr ',' '\n' | sed -n '8p' | tr -dc [:digit:]'\n' # number of messages
	grep -oh 'received [0-9]* merge' "$FILE" | tr -d [' 'a-z] # number of merges performed

}


main(){
	local FILE=$1

	if [[ ! -f "$FILE" ]]; then
		echo "Can't find file: ${FILE}"
		exit
	fi

	process "${FILE}"
}

main $*