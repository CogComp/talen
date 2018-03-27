#!/bin/sh
if [ ! -f config/users.txt ]; then
    echo "config/users.txt not found. Try running: python scripts/quickstart.py"
    exit
fi


mvn spring-boot:run -Drun.addResources=true
