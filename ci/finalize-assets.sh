#!/usr/bin/env bash

ASSETS=src/main/assets/defaults
mkdir -p $ASSETS
cd $ASSETS
touch .nomedia
# Finds all directories, excluding the current directory, and removes the ./ prefix
find -L . ! -path . -type d | sed -E "s|^\./||" > ../index.txt
# Finds all files and directories, excluding the current directory, and removes the ./ prefix
find -L . ! -path . | sed -E "s|^\./||" > ../filelist.txt