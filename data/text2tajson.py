#!/usr/bin/python
import sys,os
import ccg_nlpy
import json
import codecs

# Install ccg_nlpy with:
# $ pip install ccg_nlpy

# This file converts a folder full of text files (one sentence per line, whitespace tokenized)
# into a folder of tajson files.

def lines2json(lines):
    """ This takes a set of lines (read from some text file)
    and converts them into a JSON TextAnnotation. This assumes
    that there is one sentence per line, whitespace tokenized. """
    
    doc = {}
    doc["corpusId"] = infolder
    doc["id"] = fname

    sents = {}
    sentends = []               
    tokens = []

    for sent in lines:
        toks = sent.split()
        tokens.extend(toks)
        sentends.append(len(tokens))

    doc["text"] = " ".join(tokens)
    doc["tokens"] = tokens

    sents["sentenceEndPositions"] = sentends
    sents["score"] = 1.0
    doc["sentences"] = sents
    doc["views"] = []

    return doc
    

if __name__ == "__main__":
    infolder = sys.argv[1]
    outfolder = sys.argv[2]

    if not os.path.exists(outfolder):
        os.mkdir(outfolder)

    for fname in os.listdir(infolder):
        with open(infolder + "/" + fname) as f:
            lines = f.readlines()
        with codecs.open(outfolder + "/" + fname, "w", encoding="utf-8") as out:
            doc = lines2json(lines)
            json.dump(doc, out, sort_keys=True, indent=4, ensure_ascii=False)
