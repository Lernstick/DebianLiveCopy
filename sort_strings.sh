#!/bin/sh
for i in src/ch/fhnw/dlcopy/Strings*
do
	sort $i>tmp
	mv tmp $i
done
