#!/bin/sh
if [ ! -f config/users.txt ]; then
    echo "config/users.txt not found... creating with default username and password (user/user)";
    echo "user user" > config/users.txt;
fi

mkdir -p logs
mkdir -p dicts

mvn spring-boot:run
