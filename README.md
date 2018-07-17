
<!--
<img src="/src/main/resources/static/img/logo-black-trans.png" width="50%" />
-->

# TALEN: Tool for Annotation of Low-resource ENtities

A lightweight web-based tool for annotating word sequences.

![Screenshot of web interface](/src/main/resources/static/img/selection.png?raw=true "Screenshot")



## Installation

Requires Java 8 and Maven. Run:

    $ ./scripts/run.sh

This will start the server on port 8009. Point a browser to [localhost:8009](http://localhost:8009). The port number is specified in [`application.properties`](./src/main/resources/application.properties).

This reads from [`config/users.txt`](config/users.txt), which has a username and password pair on each line. You will
log in using one of those pairs, and then that username is tied to your activities in that session. All annotations
that you do will be written to a path called `<orig>-annotation-<username>`, where `<orig>` is the original path
specified in the config file, and `<username>` is what you chose as username.

Suppose you do some annotations, then leave the session, and come back again. If you log in with the same
username as the previous session, it will reload all of the annotations right where you left off, so no
work is lost.

## Usage

You make annotations by clicking on words and selecting a label. If you want to remove a label, right click on a word.

To annotate a phrase, highlight the phrase, ending with the mouse in the middle of the last word. The standard box will
  show up, and you can select the correct label. To dismiss the annotation box, click on the word it points to.

A document is saved by pressing the Save button. If you navigate away using
the links on the top of the page, the document is not saved. 

## Configuration

There are two kinds of config files, corresponding to the two annotation methods
(see below). The document-based method looks for config files that start with 'doc-'
and the sentence-based method looks for config files that start with 'sent-'.

See the [example config files](config/) for the minimally required set of options.

## Annotation Methods

There are two main annotation methods supported: document-based, and sentence-based. 

### Document-based
The document-based method is a common paradigm. You point the software to a folder of documents
and each is displayed in turn, and you annotate them.

### Sentence-based  
The sentence-based method is intended to allow a rapid annotation process. First, you need to
build an index using `TextFileIndexer.java`, then you supply some seed names
in the config file. The system searches for these seed names in the index, and returns 
a small number of sentences containing them. The annotator is encouraged to annotate
these correctly, and also annotate any other names which may appear. These new names then 
join the list of seed names, and annotation continues. 

For example, if the seed name is 'Pete Sampras', then we might hope that 'Andre Agassi'
will show up in the same sentence. If the annotator chooses to annotate
'Andre Agassi' also, then the system will retrieve new sentences containing 'Andre Agassi'.
Presumably these sentences will contain entities such as 'Wimbledon' and 'New York City'. In principle,
this will continue until some cap on the number of entities has been reached.

#### Using the sentence-based

First, you need to download a corpus. We have used Hindi for this. Run:

```bash
$ (If you don't already have nltk) sudo pip install -U nltk 
$ python -m nltk.downloader indian
```

Now convert this:
```bash
$ cd data
$ python data/getindian.py
$ cd ..
```

You'll notice that this created files in `data/txt/hindi` and in `data/tajson/hindi`. Now build the index:
```bash
$ mvn dependency:copy-dependencies
$ ./scripts/buildindex.sh data/tajson/hindi/ data/index_hindi 
```

That's it! There is already a config file called `config/sent-Hindi.txt` that should get you started.


## Non-speaker Helps
One major focus of the software is to allow non-speakers of a language to 
annotate text. Some features are: inline dictionary replacement, morphological 
awareness and coloring, entity propagation, entity suggestions, hints based on frequency and 
mutual information.

### How to build an index
Use [`buildindex.sh`](scripts/buildindex.sh) to build a local index for the sentence based mode. The `indexdir` variable
will be put in the sentence-based config file. This, in turn calls `TextFileIndexer.java`.

## Command line tool
We also ship a lightweight command line tool for TALEN. This tool will read a folder of JSON TextAnnotations (more formats coming soon)
and spin up a Java-only server, serving static HTML versions of each document. This will be used only for examination and exploration.

Install it as follows:
```bash
$ ./scripts/install-cli.sh
$ export PATH=$PATH:$HOME/software/talen/
```  

(You can change the `INSTALLDIR` in `install-cli.sh` if you want it installed somewhere else). Now it is installed, you can run it 
from any folder in your terminal:

```bash
$ talen-cli FolderOfTAFiles
```

This will serve static HTML documents at `localhost:PORT` (default `PORT` is 8008). You can run with additional options:

```bash
$ talen-cli FolderOfTAFiles -roman -port 8888
```

Where the `-roman` option uses the `ROMANIZATION` view in the TextAnnotation for text (if available), and the `-port` option
uses the specified port.


## Mechanical Turk
Although the main function of this software is a server based system, there is also a lightweight version that runs
entirely in Javascript, for the express purpose of creating Mechanical Turk jobs.

The important files are [mturkTemplate.html](src/main/resources/templates/mturk/mturkTemplate.html) and [annotate-local.js](src/main/resources/static/js/annotate-local.js). The
latter is a version of [annotate.js](src/main/resources/static/js/annotate.js), but the code to handle adding and
removing spans is included in the Javascript instead of sent to a Java controller. This is less powerful (because we have
NLP libraries written in Java, not Javascript), but can be run with no server.


All the scripts needed to create this file are included in this repository. It was created as follows:

```bash
$ python scripts/preparedata.py preparedata data/txt tmp.csv
$ python scripts/preparedata.py testfile tmp.csv docs/index.html
```

[mturkTemplate.html](src/main/resources/templates/mturk/mturkTemplate.html) has a lot of extra stuff (instructions, annotator test, etc) which
can all be removed if desired. I found it was useful for mturk tasks. When you create the mturk task, there will be a 
submit button, and the answer will be put into the `#finalsubmission` field. The output string is a Javascript list of token spans along with 
label. 


## Citation

If you use this in your research paper, please cite us!

```
@inproceedings{talen2018,
    author = {Stephen Mayhew, Dan Roth},
    title = {TALEN: Tool for Annotation of Low-resource ENtities},
    booktitle = {ACL System Demonstrations},
    year = {2018},
}
```

Read the paper here: [http://cogcomp.org/papers/MayhewRo18.pdf](http://cogcomp.org/papers/MayhewRo18.pdf) 


