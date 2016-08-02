# Annotation Interface for Named Entity Recognition

![Screenshot of web interface](/src/main/resources/static/img/screenshot.png?raw=true "Screenshot")

A tool for annotating word sequences. 

Note: this requires a file called config/folders.txt that maps a folder name (anything you want) to a path. This
path must contain serialized TextAnnotations.

## Configuration

On startup, this reads a file called [config/folders.txt](config/folders.txt), which contains paths to folders containing
documents to annotate. The format of the file is

    nickname	path	format

where format is either `ta` if the files are serialized TextAnnotations, or `conll` if the files are in CoNLL NER format. See
[data/eng-conll/eng.conll](data/eng-conll/eng.conll) for an example of CoNLL NER format. 

TextAnnotations are the core datastructure from [illinois-cogcomp-nlp](https://github.com/IllinoisCogComp/illinois-cogcomp-nlp), from [University of Illinois CogComp group](http://cogcomp.cs.illinois.edu/). 

## Usage

Requires Java 8 and Maven. Run:

    $ ./run.sh

This will start the server on port 8080. Point a browser to [localhost:8080](http://localhost:8080).

It will ask you to specify a username, which is then tied to your activities in that session. All annotations
that you do will be written to a path called `<orig>-annotation-<username>`, where `<orig>` is the original path
specified in `folders.txt`, and `<username>` is what you chose as username.

Suppose you do some annotations, then leave the session, and come back again. If you log in with the same
username as the previous session, it will reload all of the annotations right where you left off, so no
work is lost.

You make annotations by clicking on words and selecting a label. If you want to remove a label, you can either press the No Label
button, or you can right click on a word.

A document is saved either by pressing the Save button, or by pressing the Next or Previous buttons. If you navigate away using
the links on the top of the page, the document is not saved. 

Currently the labels supported are LOC, ORG, GPE, and PER. These can be changed easily (just grep, and replace), and will be
generalized in a future version of this interface. 

