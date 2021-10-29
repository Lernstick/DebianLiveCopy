#!/bin/sh

# Copy the changes to the VM
scp -rCq $PWD user@$1:~/Dokumente/

# Connect to the VM
ssh user@$1 cd /home/user/Dokumente/storage-media-management/
ssh user@$1 ant bin-jar
