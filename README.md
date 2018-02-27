# Annotation Interface for Named Entity Recognition

![Screenshot of web interface](/src/main/resources/static/img/screenshot.png?raw=true "Screenshot")

A tool for annotating word sequences. (This is out of date)

## Configuration

On startup, this reads every file in config/. Each file is assumed to be a config folder for a specific language. These
files contain certain variables that should be filled out.

* name
* folderpath -- path to the conll folder (if the type is conll)
* indexpath -- path to a lucene index (created by TextFileIndexer.java)
* dictionary -- path to a Masterlex format dictionary
* labels -- specify labels and colors, for example, ORG:lightblue LOC:greenyellow PER:yellow GPE:coral
* terms -- a list of seed terms for the sentences-based tool
* suffixes

All files are assumed to be written in the CoNLL NER format. See
[data/eng-conll/eng.conll](data/eng-conll/eng.conll) for an example of CoNLL NER format. The internal datastructure
is the TextAnnotation, which is the core datastructure from [illinois-cogcomp-nlp](https://github.com/IllinoisCogComp/illinois-cogcomp-nlp), from [University of Illinois CogComp group](http://cogcomp.cs.illinois.edu/).

It is allowable to have extra parameters in the config file. Use the pound sign for comments.

## Usage

Requires Java 8 and Maven. Run:

    $ ./scripts/run.sh

This will start the server on port 8009. Point a browser to [localhost:8080](http://localhost:8009). The port number is specified in [`application.properties`](./src/main/resources/application.properties).

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

A document is saved either by pressing the Save button. If you navigate away using
the links on the top of the page, the document is not saved. 


## Structure of the Code

NOTE: this will only save sentences that have at least one annotation in them.

(This is for the sentences functionality).

There are 3 important data structures throughout the code: the SentenceCache, the groups, and the SessionData.

### SentenceCache

