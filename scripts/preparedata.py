#!/home/stephen/anaconda3/bin/python
import csv


def preparedata(fname):

    outname = fname.split(".")[0] + ".fix.csv"
    
    with open(fname) as csvfile, open(outname, "w", newline='') as out:
        reader = csv.reader(csvfile)
        writer = csv.writer(out)

        for i, row in enumerate(reader):
            if i == 0:
                header = row
                writer.writerow(header)
                continue

            d = dict(zip(header, row))

            towrite = []
            for k in header:
                if "HTMLTEXT" in k:
                    num = k[-1]
                    docid = d["DOCID" + num]
                    htmlformat = "<span class='token pointer' id='{0}'>{1}</span>"
                    htmlstring = ""
                    i = 0
                    for tok in d[k].split(" "):
                        tokid = "tok-{}-{}".format(docid, i)
                        htmlstring += htmlformat.format(tokid, tok)
                        i += 1
                    d[k] = htmlstring
                towrite.append(d[k])
            writer.writerow(towrite)


if __name__ == "__main__":
    preparedata("data/mturk.csv")
