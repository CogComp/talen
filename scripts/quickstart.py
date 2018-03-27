#!/usr/bin/python
import os

# First we write to users.
print("First we create users...")
users = {}

if os.path.exists("config/users.txt"):
    print("FYI: config/users.txt exists, this will append to it")

while True:
    uname = input("username (empty to continue) >>\n")
    if len(uname.strip()) == 0:
        break        
    pwd = input("password for {} >>\n".format(uname))
    users[uname] = pwd



with open("config/users.txt", "a") as out:
    for uname in users:
        out.write("{}\t{}\n".format(uname, users[uname]))


print("On to the configuration options...")
options = []
mode = input("annotation mode (document or sentence) >>\n")

while mode not in ["document", "sentence"]:
    mode = input("please choose document or sentence >>\n")
    
if mode == "document":
    name = input("name of this dataset: >>\n")
    options.append(("name", name))
    folderpath = input("path to folder: >>\n")
    options.append(("folderpath", folderpath))
    foldertype = input("type of files (conll or ta) >>\n")
    options.append(("type", foldertype))
    dictionary = input("dictionary (empty if no dictionary): >>\n")
    if len(dictionary.strip()) > 0:
        options.append(("dictionary", dictionary))
    labels = input("label set (can give colors also example: PER:yellow ORG:lightblue): >>\n")
    options.append(("labels", labels))

    configname = "config/doc-{}.txt".format(name)
    if os.path.exists(configname):
        print("File with path '{}' already exists please rename or delete before continuing".format(configname))
    else:
        print("Writing to",configname)
        with open(configname, "w") as out:
            for opt in options:
                out.write("{}\t{}\n".format(opt[0], opt[1]))
    
elif mode == "sentence":
    name = input("name of this dataset: >>\n")
    options.append(("name", name))
    
    folderpath = input("path to folder: >>\n")
    options.append(("folderpath", folderpath))
    
    dictionary = input("dictionary (empty if no dictionary): >>\n")    
    if len(dictionary.strip()) > 0:
        options.append(("dictionary", dictionary))

    terms = input("Seed terms (sep by comma) >>\n")
    options.append(("terms", terms))

    indexpath = input("Index path (see TextFileIndexer.java) >>\n")
    options.append(("indexpath", indexpath))
    
    labels = input("label set (can give colors also example: PER:yellow ORG:lightblue): >>\n")
    options.append(("labels", labels))

    configname = "config/sent-{}.txt".format(name)
    
    if os.path.exists(configname):
        print("File with path '{}' already exists please rename or delete before continuing".format(configname))
    else:
        print("Writing to",configname)
        with open(configname, "w") as out:
            for opt in options:
                out.write("{}\t{}\n".format(opt[0], opt[1]))

