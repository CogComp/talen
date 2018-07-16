#!/usr/bin/python
import os
from nltk.corpus import indian

# can choose from: marathi, bangla, telugu, hindi
lang = "hindi"

if not os.path.exists("txt/" + lang):
    os.mkdir("txt/" + lang)

sents = indian.sents(lang + ".pos")

# arbitrarily put 10 sentences per document. 
for i in range(0,len(sents),10):
    with open("txt/" + lang + "/" + str(i), "w") as out:
        for sent in sents[i:i+10]:    
            out.write(" ".join(sent) + "\n")
