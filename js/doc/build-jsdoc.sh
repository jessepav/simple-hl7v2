#!/bin/bash

cd "$(dirname "$0")"

mkdir -p jsdoc
jsdoc -c jsdoc-config.json -d jsdoc ../src/simple_hl7.mjs
