#!/home/stephen/anaconda3/bin/python

# open up csv file.
# with the right fields.
# drop template into a very standard html file.
# string replace fields
import csv

with open("src/main/resources/templates/mturkTemplate.html") as f:
    template = f.read()

with open("data/mturk.csv") as csvfile:
    reader = csv.reader(csvfile)

    for i, row in enumerate(reader):
        if i == 0:
            header = row
            continue

        d = dict(zip(header, row))
        
        for k in header:
            v = d[k]
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
                    
                v = htmlstring
            template = template.replace("${{{}}}".format(k), v)


with open("tmp.html", "w") as out:
    out.write("<!DOCTYPE html><html><body>")
    out.write(template)
    out.write("</body></html>")
    
