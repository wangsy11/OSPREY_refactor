#!/bin/csh -f

set jdep=/home/home3/sw283/github/osprey_refactor/lib
set jars=$jdep/architecture-rules-3.0.0-M1.jar:$jdep/colt-1.2.0.jar:$jdep/commons-beanutils-1.6.jar:$jdep/commons-collections4-4.1.jar:$jdep/commons-digester-1.6.jar:$jdep/commons-io-1.4.jar:$jdep/commons-lang-2.5.jar:$jdep/commons-logging-1.1.1.jar:$jdep/commons-math3-3.0.jar:$jdep/commons-math3-3.3.jar:$jdep/gluegen-rt-2.3.2-natives-linux-amd64.jar:$jdep/gluegen-rt-2.3.2-natives-macosx-universal.jar:$jdep/gluegen-rt-2.3.2.jar:$jdep/hamcrest-all-1.3.jar:$jdep/jcuda-0.8.0RC.jar:$jdep/jcuda-natives-0.8.0RC-linux-x86_64.jar:$jdep/jcuda-natives-0.8.0RC-windows-x86_64.jar:$jdep/jdepend-2.9.1.jar:$jdep/jocl-2.3.2-natives-linux-amd64.jar:$jdep/jocl-2.3.2-natives-linux-amd64.jar:$jdep/jocl-2.3.2-natives-macosx-universal.jar:$jdep/jocl-2.3.2.jar:$jdep/joptimizer.jar:$jdep/junit-4.12.jar:$jdep/log4j-1.2.14.jar:$jdep/ojalgo-40.0.0.jar:$jdep/xml-apis-1.0.b2.jar

set curdir=`pwd`
set tempdir='/home/home3/sw283/github/osprey_refactor/rotamer/K04-rot/temp'
rm -r $tempdir/*
mkdir -p $tempdir
set javaDir=/home/home3/sw283/github/osprey_refactor/build/classes

set hostname=`hostname`
setenv GRB_LICENSE_FILE "/home/home1/kroberts/gurobiLicenses/$hostname/gurobi.lic"
setenv LD_LIBRARY_PATH /usr/project/dlab/Code/Other-people2/gurobi560/linux64/lib:/usr/pkg/mpiJava-1.2.5-mpich2-smpd/lib:/usr/pkg/mpich2-1.0.7-smpd-shared/lib

java -cp ${javaDir}:$jars -Xms4g -Xmx150g -Xss256m edu.duke.cs.osprey.control.Main -c KStar.cfg findGMEC System.cfg DEE.cfg > $tempdir/log.out
