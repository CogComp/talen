#!/bin/sh
if [ ! -f config/users.txt ]; then
    echo "config/users.txt not found. Try running: python scripts/quickstart.py"
    exit
fi

mkdir -p logs
mkdir -p dicts

mvn spring-boot:run
