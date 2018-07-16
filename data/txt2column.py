import sys,os

infolder = sys.argv[1]
outfolder = sys.argv[2]

os.mkdir(outfolder)

for fname in os.listdir(infolder):
    with open(infolder + "/" + fname) as f:
        lines = f.readlines()
    with open(outfolder + "/" + fname, "w") as out:
        for sent in lines:
            toks = sent.split()
            for tok in toks:
                out.write("{} O\n".format(tok))
            out.write("\n")

