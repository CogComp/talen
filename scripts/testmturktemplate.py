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

        for k, v in zip(header, row):
            template = template.replace("${{{}}}".format(k), v)


with open("tmp.html", "w") as out:
    out.write("<!DOCTYPE html><html><body>")
    out.write(template)
    out.write("</body></html>")
    
