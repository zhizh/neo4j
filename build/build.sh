#!/bin/sh

pushd ..
#export MAVEN_OPTS="-Xmx512m -XX:MaxPermSize=256m"
export MAVEN_OPTS="-Xmx512m" 
mvn clean install -DskipTests -DmyBuild
popd

