#!/bin/sh
for i in src/main/java/ch/fhnw/dlcopy/Strings*
do
	sort $i>tmp
	mv tmp $i
done
