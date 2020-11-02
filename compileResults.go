package main

import (
	"bufio"
	"fmt"
	"log"
	"math"
	"os"
	"strconv"
	"strings"
)

func main() {
	// Either process the specific experiments given or do all of them.
	args := os.Args[1:]
	if len(args) == 0 {
		args = []string{"T1", "T2", "T3", "T4", "T5", "T6"}
	}
	if len(args) == 1 && args[0][len(args[0])-4:] == ".out" {
		mean, dev := readLog(args[0])
		fmt.Printf("Mean run time: %.0fms, Standard deviation: %.1fms\n", mean, dev)
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
	file, err := os.Create(name + ".dat")
	check(err)

	defer file.Close()

	w := bufio.NewWriter(file)

	// Grab the first line from one file for info
	info := readLogInfo("logs/" + name + "-3A.out")
	_, err = w.WriteString(info + "\n")
	check(err)

	// Write results
	replicas := []string{"3", "5", "9"}
	types := []string{"A", "B", "C"}
	for _, r := range replicas {
		_, err = w.WriteString(r)
		check(err)
		for _, t := range types {
			mean, dev := readLog("logs/" + name + "-" + r + t + ".out")
			line := fmt.Sprintf(" %.2f %.2f", mean, dev)
			_, err = w.WriteString(line)
			check(err)
		}
		_, err = w.WriteString("\n")
		check(err)
	}
	w.Flush()
}

// Get info from a log
func readLogInfo(name string) string {
	// Try and open file
	file, err := os.Open(name)
	check(err)
	defer file.Close()

	scanner := bufio.NewScanner(file)
	_ = scanner.Scan()
	line := scanner.Text()
	if err := scanner.Err(); err != nil {
		check(err)
	}
	return line
}

// Takes a log in condensed for and produces a mean and standard deviation.
func readLog(name string) (float64, float64) {
	// Try and open file
	file, err := os.Open(name)
	check(err)
	defer file.Close()

	scanner := bufio.NewScanner(file)
	var times []int
	times = make([]int, 0, 100)

	// Read each line and calculate the runtime.
	for scanner.Scan() {
		line := scanner.Text()
		// Skip comments
		if line[0] == '#' {
			continue
		}
		nums := strings.Split(line, " ")
		start, _ := strconv.ParseInt(nums[0], 10, 64)
		finish, _ := strconv.ParseInt(nums[1], 10, 64)
		diff := int(finish - start)
		times = append(times, diff)
	}

	if err := scanner.Err(); err != nil {
		check(err)
	}

	// Calculate sum and standard deviation.
	sum := 0
	for _, num := range times {
		sum += num
	}
	mean := float64(sum) / float64(len(times))

	if len(times) == 1 {
		return mean, 0.0
	}

	sd := 0.0
	for _, num := range times {
		sd += math.Pow(float64(num)-mean, 2.0)
	}
	sd = math.Sqrt((1.0 / float64(len(times)-1)) * sd)

	return mean, sd
}

func check(e error) {
	if e != nil {
		log.Fatal(e)
	}
}
