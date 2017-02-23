#!/bin/sh
mvn spring-boot:run -Drun.addResources=true -Dserver.port=$1
