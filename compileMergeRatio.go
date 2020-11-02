package main

import (
	"bufio"
	"fmt"
	"log"
	"os"
	"strconv"
)

func main() {
	// Either process the specific experiments given or do all of them.
	args := os.Args[1:]
	if len(args) == 0 {
		args = []string{"T1", "T2", "T3", "T4", "T5", "T6"}
	}
	if len(args) == 1 && args[0][len(args[0])-4:] == ".out" {
		queries, merges := readLog(args[0])
		fmt.Printf("Queries: %d, Merges: %d, Ratio (merges:queries): 1:%d\n", queries, merges, queries / merges)
		return
	}
	for _, experiment := range args {
		processExperiment(experiment)
	}
}

// Read all 9 logs for a experiment and output a new file.
// Expecting something like T1. files in logs/
func processExperiment(name string) {
	// Setup
	file, err := os.Create(name + "_ratio.dat")
	check(err)

	defer file.Close()

	w := bufio.NewWriter(file)

	// Write results
	replicas := []string{"3", "5", "9"}
	types := []string{"A", "B", "C"}
	for _, r := range replicas {
		_, err = w.WriteString(r)
		check(err)
		for _, t := range types {
			queries, merges := readLog("logs/" + name + "-" + r + t + "_ratio.out")
			line := fmt.Sprintf(" %d %d, 1:%d", queries, merges, queries / merges)
			_, err = w.WriteString(line)
			check(err)
		}
		_, err = w.WriteString("\n")
		check(err)
	}
	w.Flush()
}

// Takes a log in condensed for and produces a count for merges and queries performed.
func readLog(name string) (int64, int64) {
	// Try and open file
	file, err := os.Open(name)
	check(err)
	defer file.Close()

	scanner := bufio.NewScanner(file)
	var runs int64 = 0
	var queries int64 = 0
	var merges int64 = 0

	// Read each line and calculate the number of queries. Format is:
	// "runs[\n]queries[\n]merges[\n]...merges[\n]"
	for scanner.Scan() {
		line := scanner.Text()
		num, _ := strconv.ParseInt(line, 10, 64)
		if runs == 0 {
			runs = num
		} else if queries == 0 {
			queries = num
		} else {
			merges += num
		}
	}

	if err := scanner.Err(); err != nil {
		check(err)
	}

	return runs*queries, merges
}

func check(e error) {
	if e != nil {
		log.Fatal(e)
	}
}