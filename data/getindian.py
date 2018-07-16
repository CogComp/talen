#!/usr/bin/python
import os
from nltk.corpus import indian
import txt2tajson

# can also choose from: marathi, bangla, telugu, hindi
lang = "hindi"

if not os.path.exists("txt/" + lang):
    os.mkdir("txt/" + lang)

sents = indian.sents(lang + ".pos")

# arbitrarily put 10 sentences per document.
num = 0
for i in range(0,len(sents),10):
    with open("txt/" + lang + "/" + str(i), "w") as out:
        for sent in sents[i:i+10]:    
            out.write(" ".join(sent) + "\n")
    num += 1

print("Wrote {} text files to {}".format(num, "txt/" + lang))

# Now convert txt to tajson.
txt2tajson.convert("txt/" + lang, "tajson/" + lang)

print("Now run:\n  $ ./scripts/buildindex.sh data/tajson/hindi/ data/index_hindi")
