#!/usr/bin/env bash

visibility=${1:-protected}

[[ $visibility == "private" ]] && linksrc_arg="-linksource" || linksrc_arg=""

output_dir=${output_dir:-build/javadoc}

cd $(dirname $0)/..

mkdir -p $output_dir

javadoc -classpath "lib/*:build/production/simple-hl7v2" -$visibility $linksrc_arg \
        -sourcepath src -subpackages com.illcode.hl7 -d $output_dir \
        -windowtitle "Simple-HL7v2 Javadocs" -doctitle "Simple-HL7v2 Javadocs"
