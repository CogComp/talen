#!/home/stephen/anaconda3/bin/python
import csv
import os

LABELS = ["LOC", "PER", "ORG"]
LANGUAGE = "English"


def testfile(infile, outfile):
    """This method takes an ordinary input csv file
    and writes to a test html file. This is just
    to test locally before submitting to mechanical turk"""

    with open("src/main/resources/templates/mturk/mturkTemplate.html") as f:
        template = f.read()

    print("Reading just the header and first row of", infile)
    with open(infile) as csvfile:
        reader = csv.reader(csvfile, dialect="excel")

        header = next(reader)
        row1 = next(reader)

        d = dict(zip(header, row1))

        for k in header:
            v = d[k]
            template = template.replace("${{{}}}".format(k), v)

    print("Writing to", outfile)
    with open(outfile, "w") as out:
        out.write("<!DOCTYPE html><html>")
        out.write("<body>\n")
        out.write(template)
        out.write("<script src=\"../src/main/resources/static/js/annotate-local.js\" type=\"text/javascript\"></script>\n")
        out.write("</body></html>")


def preparedata(folder, outname):
    """This method takes as input a folder of text files,
     each with one sentence per line, and
    writes to a csv file with appropriate html in each
    field."""

    header = ["DOCID","HTMLTEXT","LABEL1","LABEL2","LABEL3","LANGUAGE"]

    fnames = sorted(os.listdir(folder))

    print("Writing to", outname)
    out = open(outname, "w", newline='')
    out.write(",".join(header) + "\n")

    for fname in fnames:
        with open(os.path.join(folder, fname)) as f:
            lines = f.readlines()

            # First DOCID
            outwrite = []
            docid = fname
            for c in [".", ":"]:
                docid = docid.replace(c, "_")
            outwrite.append(docid)

            HTMLTEXT = ""
            i = 0
            for line in lines:
                line = line.strip()
                line = line.replace("\"", "\"\"")

                htmlformat = "<span class='token pointer' id='{0}'>{1}</span>"

                HTMLTEXT += "<p id=p{}>".format(i)
                for tok in line.split():
                    tokid = "tok-{}-{}".format(docid, i)
                    HTMLTEXT += htmlformat.format(tokid, tok)
                    i += 1
                HTMLTEXT += "</p>"

            outwrite.append("\"" + HTMLTEXT + "\"")
            outwrite.extend(LABELS)
            outwrite.append(LANGUAGE)
            out.write(",".join(outwrite) + "\n")


    out.close()


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Prepare data for mechanical turk submission")
    parser.add_argument("method", choices=["preparedata", "testfile"])
    parser.add_argument("infile")
    parser.add_argument("outfile")

    args = parser.parse_args()

    locals()[args.method](args.infile, args.outfile)

