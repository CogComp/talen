

<img src="/src/main/resources/static/img/logo-black-trans.png" width="50%" />

# TALEN: Tool for Annotation of Low-resource ENtities.

A lightweight web-based tool for annotating word sequences.

![Screenshot of web interface](/src/main/resources/static/img/screenshot.png?raw=true "Screenshot")


## Lightweight Demo

See a lightweight Javascript-only demo here: [cogcomp.github.io/talen/](http://cogcomp.github.io/talen/)

## Usage

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

You make annotations by clicking on words and selecting a label. If you want to remove a label, right click on a word.

To annotate a phrase, highlight the phrase, ending with the mouse in the middle of the last word. The standard box will
  show up, and you can select the correct label. To dismiss the annotation box, click on the word it points to.

A document is saved by pressing the Save button. If you navigate away using
the links on the top of the page, the document is not saved. 

## Configuration

There are two kinds of config files, corresponding to the two annotation methods
(see below). The document-based method looks for config files that start with 'doc-'
and the sentence-based method looks for config files that start with 'sent-'.

Describe config files, and how they are different for document and sentence.




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




## Non-speaker Helps
One major focus of the software is to allow non-speakers of a language to 
annotate text. Some features are: inline dictionary replacement, morphological 
awareness and coloring, entity propagation, entity suggestions, hints based on frequency and 
mutual information.

### How to build an index
Run `TextFileIndexer.java`, but modify the strings in the main method. The `indexdir` variable
will be put in the sentence-based config file.

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
