<img src="https://www.artipie.com/logo.svg" width="64px" height="64px"/>

[![Maven Central](https://img.shields.io/maven-central/v/com.artipie/helm-adapter.svg)](https://maven-badges.herokuapp.com/maven-central/com.artipie/helm-adapter)
[![EO principles respected here](https://www.elegantobjects.org/badge.svg)](https://www.elegantobjects.org)

[![Javadoc](http://www.javadoc.io/badge/com.artipie/helm-adapter.svg)](http://www.javadoc.io/doc/com.artipie/helm-adapter)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/artipie/helm-adapter/blob/master/LICENSE.txt)
[![Hits-of-Code](https://hitsofcode.com/github/artipie/helm-adapter)](https://hitsofcode.com/view/github/artipie/helm-adapter)
[![PDD status](http://www.0pdd.com/svg?name=artipie/helm-adapter)](http://www.0pdd.com/p?name=artipie/helm-adapter)

# Helm adapter

An Artipie adapter which allow you to host helm carts.  

## Upload a chart

Since helm doesn't officially support chart uploading, the following way is
recommended to use:

```bash
curl --data-binary "@mychart-0.1.0.tgz" http://example.com
```

This is way is similar to the
[artifactory](https://www.jfrog.com/confluence/display/JFROG/Helm+Chart+Repositories)
and
[chartmuseum](https://github.com/helm/chartmuseum#uploading-a-chart-package)
approaches.

## Useful links

[The Chart Repository Guide](https://helm.sh/docs/topics/chart_repository/) - describes repository 
structure and content of the `index.yml` file.

## How to contribute

Fork repository, make changes, send us a pull request. We will review
your changes and apply them to the `master` branch shortly, provided
they don't violate our quality standards. To avoid frustration, before
sending us your pull request please run full Maven build:

## Benchmarking

To run benchmarks:
 1. Build `helm-bench` with `mvn package` from folder `benchmarks`
 2. Copy dependencies to `target/dependency/` dir: `mvn dependency:copy-dependencies`
 3. Create directory for tests and copy test resources to this directory
 4. Run benchmarks with `env BENCH_DIR=/tmp/helm-test java -cp "target/benchmarks.jar:target/classes/*:target/dependency/*" org.openjdk.jmh.Main BenchToRun`, where `/tmp/helm-test` is a directory with test data.

There are available bundles:
  - https://artipie.s3.amazonaws.com/helm-test/helm100.tar.gz
  
Available benchmark classes:
 
### HelmAstoRemoveBench 

This benchmark class removes Helm charts from the repository index files, it works with 
`com.artipie.helm.Helm.Asto#delete(Collection<Key>, Key)` method and requires unpacked tgz archives in the test directory
and index file which contains information about them. Available bundles are specified above.

### HelmAstoAddBench

`HelmAstoAddBench` updates repository metadata with the collection of the provided keys of charts, 
it works with `com.artipie.helm.Helm.Asto#add(Collection<Key>, Key)` method and requires unpacked tgz archives to add 
in the test directory. Available bundles are specified above.
