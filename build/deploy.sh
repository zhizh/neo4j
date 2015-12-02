#!/bin/sh
bash ~/run/bin/neo4j stop
tar -xzvf ../packaging/standalone/target/neo4j-community-2.3.2-SNAPSHOT-unix.tar.gz --exclude data 
cp -f -r neo4j-community-2.3.2-SNAPSHOT/*  ~/run
bash ~/run/bin/neo4j start

