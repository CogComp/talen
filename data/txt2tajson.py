#!/usr/bin/python
import codecs
import json
import os
import sys

# This file converts a folder full of text files (one sentence per line, whitespace tokenized)
# into a folder of tajson files.

# Usage:
# $ txt2tajson.py input_folder output_folder


def lines2json(lines, fname):
    """ This takes a set of lines (read from some text file)
    and converts them into a JSON TextAnnotation. This assumes
    that there is one sentence per line, whitespace tokenized. """

    doc = {}
    doc["corpusId"] = ""
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
    sents["generator"] = "txt2tajson.py"
    doc["sentences"] = sents
    doc["views"] = []

    return doc


def convert(infolder, outfolder):
    if not os.path.exists(outfolder):
        os.mkdir(outfolder)

    for fname in os.listdir(infolder):
        with open(infolder + "/" + fname) as f:
            lines = f.readlines()
        with codecs.open(outfolder + "/" + fname, "w", encoding="utf-8") as out:
            doc = lines2json(lines, fname)
            json.dump(doc, out, sort_keys=True, indent=4, ensure_ascii=False)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: txt2tajson.py input_folder output_folder")
        exit(1)

    infolder = sys.argv[1]
    outfolder = sys.argv[2]
    convert(infolder, outfolder)
