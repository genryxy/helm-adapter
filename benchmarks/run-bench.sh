#!/bin/bash

# parameters:
#  - bench_class - name of benchmark which will run.
#  - version - version of adapter. It is used for specifying in output file name.

while [ $# -gt 0 ]; do
  case "$1" in
    --bench=*)
      bench_class="${1#*=}"
      ;;
    --version=*)
      version="${1#*=}"
      ;;
    *)
      printf "***************************\n"
      printf "* Error: Invalid argument.*\n"
      printf "***************************\n"
      exit 1
  esac
  shift
done

if [[ -n "$bench_class" ]]; then
	echo "Benchmark '$bench_class' will run"
	if [[ ! (-n "$version") ]]; then
		version="snapshot"
	fi
	output_file="$bench_class-$(date +%Y-%m-%d-%H)-$version.txt"
else
    echo "Please, specify benchmark class for running using `--bench=ClassName`"
	exit 1
fi

# Go to required directory
cd ..

# Build jar for helm-adapter
mvn clean package -DskipTests
echo "-------Helm-adapter JAR was built -------"

# Install built snapshot
mvn install -DskipTests

# Go to directory 
cd benchmarks
echo "-------Now it is in benchmarks directory-------"

# Build jar for helm-benchmarks
mvn clean package -DskipTests
echo "-------Helm-benchmarks JAR was built-------"

# Install builded snapshot
mvn install -DskipTests

# Copy dependencies
mvn dependency:copy-dependencies
echo "-------Dependencies were copied-------"

# Run benchmark
echo "-------Benchmark starts. It is time-consuming-------"
temp_out=$(mktemp)

# unix
env BENCH_DIR=../../../data/bundle100 java -cp "target/benchmarks.jar:target/classes/*:target/dependency/*" -Djmh.ignoreLock=true org.openjdk.jmh.Main "$bench_class" > "$temp_out"

# windows (pay attetion to semicolon)
# env BENCH_DIR=C:/Users/Downloads/helm100 java -cp "target\benchmarks.jar;target\classes\*;target\dependency\*" -Djmh.ignoreLock=true org.openjdk.jmh.Main "$bench_class" > "$temp_out"

echo "Results of benchmark:"
cat $temp_out

cd results
mkdir -p -v "$version"
mv $temp_out $version/$output_file
