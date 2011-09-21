#!/bin/sh
for i in src/dlcopy/Strings*
do
	sort $i>tmp
	mv tmp $i
done
