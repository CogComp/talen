/**
 * Created by stephen on 8/30/17.
 */


$(document).ready(function() {
    console.log("loading stuff...");

    if(typeof baseurl === 'undefined'){
        baseurl = "";
    }

    console.log("baseurl: " + baseurl);
    console.log("controller: " + controller);

    var highlighting = false;
    var range = {start:-1, end:-1, id:""};

    function resetrange(){
        range = {start:-1, end:-1, id:""};
        highlightrange();
    }

    // This retrieves the text within a range. If the range
    // is empty, then it returns the empty string.
    function gettextinrange(usetext){
        usetext = usetext || false;
        if(range.start != -1 && range.end != -1) {
            var txt = ""
            for (var i = range.start; i <= range.end; i++) {

                var el = document.getElementById("tok-" + range.id + "-" + i);
                if(usetext){
                    txt += $(el).text() + " ";
                }else{
                    txt += $(el).attr("orig") + " ";
                }

            }
            return txt.trim();
        }else{
            return "";
        }
    }

    // This returns a list of all words in the document.
    function getalltext() {
        var allwords = $.map($("[id^=tok]"), function (n, i) {
            return $(n).attr("orig");
        });
        return allwords;
    }


    function highlightrange(){

        $(".highlighted").removeClass("highlighted");
        $(".highlightstart").removeClass("highlightstart");
        $(".highlightend").removeClass("highlightend");
        $(".highlightsingle").removeClass("highlightsingle");

        // NOTE: jquery does not allow colons or periods in id strings. Because of
        // this, we use document.getElementById(..).

        // NOTE: for highlighting to work correctly, the token id pattern needs to
        // match the pattern found in HtmlGenerator.java

        if(range.start == -1 && range.end == -1) {
            // do nothing.
        }else if(range.start == range.end){
            var startel = document.getElementById("tok-" + range.id + "-" + range.start);
            $(startel).addClass("highlightsingle");

        }else{
            for(var i = range.start; i <= range.end; i++){
                var el = document.getElementById("tok-" + range.id + "-" + i);
                if(i == range.start){
                    $(el).addClass("highlightstart");
                }
                else if(i == range.end){
                    $(el).addClass("highlightend");
                }else{
                    $(el).addClass("highlighted");
                }
            }
        }
    }

    /** This returns true if the range has been changed. **/
    function updaterange(id){
        var docid = id.split("-")[1];
        var intid = parseInt(id.split("-")[2]);

        range.id = docid;

        var ret = false;
        if(range.start == -1 && range.end == -1){
            range.start = intid;
            range.end = intid;
            ret = true;
        }

        if(range.start - intid == 1){
            range.start = intid;
            ret = true;
        }

        if(range.end - intid == -1){
            range.end = intid;
            ret = true;
        }

        highlightrange();

        return ret;
    }

    function iscurrentrange(id){
        var docid = id.split("-")[1];
        var intid = parseInt(id.split("-")[2]);
        return range.id == docid && (range.start == intid || range.end == intid);
    }


    function loadtok(){
        console.log("loadtok called...");

        $(document).mouseup(function(event){
            highlighting = false;
        });


        // just clean everything out first...


        $("[id^=tok]").popover({
            placement: "bottom",
            content: function () {
                var html = $("#buttons").html();
                var out = " <div id='popover" + $(this)[0].id + "'>" + html + "</div>";
                return out;
            },
            title: function () {
                var text = gettextinrange(true);
                var link = "<a class='search' href=\"https://www.google.com/search?q=" + gettextinrange(true) + "\" target=\"_blank\">Google</a>"
                return text + " (" + link + ")";
            },
            html: true,
            trigger: "focus",
            animation: false,
        });


        $("[id^=tok]").mouseover(function(event){

            if(highlighting && updaterange(this.id)) {
                //$(this).addClass("highlighted");
            }
        });

        // click on anything but a word, and they hide.
        $(document).mousedown(function(e){
            console.log(e);
            var hasclass = $(e.target).hasClass("labelbutton") || $(e.target).hasClass("search");
            if(!e.target.id.startsWith("tok") && !hasclass && !(e.target.tagName == "MARK")) {
                console.log("extraneous click");
                $("[id^=tok]").popover("hide");
                resetrange();
                showtopstats();
            }
        });

        // this solves the problem where the window scrolls to
        // the top whenever definput has focus.
        var cursorFocus = function(elem) {
            var x = window.scrollX, y = window.scrollY;
            if(elem != null) {
                elem.focus();
            }
            window.scrollTo(x, y);
        }

        $("[id^=tok]").mouseup(function(event){

            // only toggle on the left click.
            if(event.which == 1) {
                $("[id^=tok]").not($(this)).popover('hide');
                $(this).popover("show");

                highlighting = false;

                // put focus on the dictionary entry element.
                cursorFocus(document.getElementById("definput"));
                $(".enter").keydown(function (event) {
                    var keypressed = event.keyCode || event.which;
                    if (keypressed == 13) {
                        submitdict();
                    }
                });

                // if(range.start > -1) {
                //     $.ajax({
                //         method: "POST",
                //         url: "/stats/getstats",
                //         data: {text: gettextinrange(), alltext: getalltext()}
                //     }).done(function (msg) {
                //         $("#infobox").html(msg);
                //     });
                // }else{
                //     showtopstats();
                // }
            }
        });

        $("[id^=tok]").mousedown(function(event){
            if(event.which == 1) {
                highlighting = true;
                resetrange();
                updaterange(this.id);
                console.log(range);
            }
        });

        $("div.text")
            .mouseenter(function(){})
            .mouseleave(function(){
                highlighting = false;
            })


        // on right click, change label to nothing.
        $("[id^=tok]").contextmenu(function(event){
            resetrange();
            $("[id^=tok]").popover('hide');
            showtopstats();
            event.preventDefault();
            var span = event.currentTarget;

            removelabel(span);
        });

    }

    loadtok();

    $("#dictbutton").click(function(){
        $.ajax({
            method: "GET",
            url: baseurl + "/" + controller + "/toggledefs",
            data: {idlist: getsentids()}
        }).done(function (msg) {
            console.log("successful toggle");
            $("#htmlcontainer").html(msg);
            loadtok();
        });

    });


    $("#romanbutton").click(function(){
        $.ajax({
            method: "GET",
            url: baseurl + "/" + controller + "/togglerom",
            data: {idlist: getsentids()}
        }).done(function (msg) {
            console.log("successful toggle");
            $("#htmlcontainer").html(msg);
            showtopstats();
            loadtok();
        });

    });


    $("#copybutton").click(function(){
        $.ajax({
            method: "GET",
            url: baseurl + "/" + controller + "/togglecopy",
            data: {idlist: getsentids()}
        }).done(function (msg) {
            console.log("successful toggle");
            $("#htmlcontainer").html(msg);
            showtopstats();
            loadtok();
        });

    });


    $("#loadtokbutton").click(function(){
        loadtok();
    });

    // when the doc loads, get the top stats.
    function showtopstats() {
        var taid = getParameterByName("taid");

        if(taid != null) {
            $.ajax({
                method: "POST",
                url: "/stats/gettopstats",
                data: {docid: taid}
            }).done(function (msg) {
                $("#infobox").html(msg);
            });
        }
    }

    showtopstats();


    // this runs when you click on a single button.
    $("body").on("click", '.popover button', function(event){
        var buttonvalue = $(this)[0].value;

        //var spanid = $(this).parents("[id^=popovertok]")[0].id;

        console.log(event);

        console.log($(this).parents());

        //var sentid = $(this).parents(".card-body")[0].id;
        var sentid = range.id;


        console.log(sentid);
        console.log("Clicked on a button..." + buttonvalue);
        console.log(range);
        console.log(event.target.id);
        console.log(event.target.id == "submitdict");

        var startid = "tok-" + range.start;
        var endid = "tok-" + (range.end+1);

        if(!(event.target.id == "submitdict")){
            $("[id^=tok]").popover("hide");

            resetrange();
            showtopstats();

            addlabel(sentid, startid, endid, buttonvalue);
        }
        
    });

    $("body").on("click", '#submitdict', submitdict);

    function submitdict(){
        // TODO: allow only single word entries at this point.
        if (range.start != range.end){
            return;
        }

        var docid = range.id;
        var tokid = "tok-" + docid + "-" + range.start ;

        var el = document.getElementById(tokid)
        var key = $(el).attr("orig");
        var val = $("#definput").val();

        $("[id^=tok]").popover("hide");

        console.log(range);
        console.log("you want to submit: " + key + ", " + val);
        $.ajax({
            method: "GET",
            url: "/dict/add",
            data: {key:key, val:val}
        }).done(function () {
            refreshsents();
        });
    };



    function getParameterByName(name) {
        var match = RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
        return match && decodeURIComponent(match[1].replace(/\+/g, ' '));
    }

    // given a span id (e.g. tok-4), return the integer
    function getnum(spanid){
        return parseInt(spanid.split("-").slice(-1)[0]);
    }

    // Given a span, remove the label.
    function removelabel(span) {

        $("#savebutton").html("<span class=\"glyphicon glyphicon-floppy-disk\" aria-hidden=\"true\"></span> Save");
        $("#savebutton").css({"border-color" : "#c00"});

        var sentid = $(span).parents(".card-body")[0].id;

        // returns just the number of the token. tok-4, returns 4.
        var tokid = getnum(span.id);

        console.log("Removing label from: " + sentid + ":" + tokid);

        $.ajax({
            method: "POST",
            url: baseurl + "/"+controller+"/removetoken",
            data: {sentid: sentid, tokid: tokid}
        }).done(function (msg) {
            console.log("successful removal.");
            refreshsents();
        });

    }

    // run this function when you click on a token.
    function addlabel(sentid, starttokid, endtokid, newclass) {
        console.log("Adding " + newclass + " to " + starttokid + "---" + endtokid + ", sentid=" + sentid);

        $("#savebutton").html("<i class=\"fa fa-floppy-o\" aria-hidden=\"true\"></i> Save");
        $("#savebutton").css({"border-color" : "#c00"});

        var srch = window.location.pathname.endsWith("/search");
        var srchanno = getParameterByName("searchinanno") == "on";

        $.ajax({
            method: "POST",
            url: baseurl + "/"+controller+"/addspan",
            data: {label: newclass,
                starttokid: getnum(starttokid),
                endtokid:getnum(endtokid),
                sentid: sentid,
                sentids: getsentids(),
                id: sentid,
                propagate: srch == srchanno }
        }).done(function () {
            refreshsents();
        });
    };

    function refreshsents(){
        var query= getParameterByName("query");
        $.ajax({
            method: "POST",
            url: baseurl+"/"+controller+"/gethtml",
            data: {sentids: getsentids(), query: query}
        }).done(function (msg) {
            $("#htmlcontainer").html(msg);
            loadtok();
            performMark();
        });
    }

    $("[id^=addlabel-]").click(function(d){

        var label = $(this).text();
        var text= getParameterByName("groupid");

        $("#savebutton").html("<span class=\"fa fa-floppy-o\" aria-hidden=\"true\"></span> Save");
        $("#savebutton").css({"border-color" : "#c00"});

        $.ajax({
            method: "GET",
            url: baseurl+"/"+controller+"/addtext",
            data: {text: text, label: label, sentids: getsentids()}
        }).done(function (msg) {
            console.log(msg);
            refreshsents();
        });
    });

    function getsentids(){
        var ids = $.map($(".text"), function(n, i){
            return n.id;
        });
        return ids;
    }

    function save(){
        var groupid = getParameterByName("groupid");
        console.log("saving group: " + groupid);

        $.ajax({
            url: baseurl+"/"+controller+"/save",
            data: {sentids: getsentids()},
            method: "POST",
            beforeSend: function() {
                // setting a timeout
                $("#savebutton").html("<i class=\"fa fa-spinner fa-spin\"></i> Saving...");
            }
        }).done(function (msg) {
            console.log("finished saving!");
            $("#savebutton").html("<i class=\"fa fa-check\" aria-hidden=\"true\"></i> Saved!");
            $("#savebutton").css({"border-color" : ""});
        });

    };
    $( "#savebutton" ).click(save);
    $( ".saveclass" ).click(save);

});



// Create an instance of mark.js and pass an argument containing
// the DOM object of the context (where to search for matches)
var markInstance = new Mark(document.querySelector("#htmlcontainer"));
// Cache DOM elements
var keywordInput = document.querySelector("input[name='keyword']");

function performMark() {
    console.log("mark");

    // Read the keyword
    var keyword = keywordInput.value;

    // Determine selected options
    var options = {
        "accuracy" : {
            "value" : "complementary",
            "limiters" : ["?"]   // this is here to apostrophes in names don't block full match.
        }
    };

    // Remove previous marked elements and mark
    // the new keyword inside the context
    markInstance.unmark({
        done: function(){
            markInstance.mark(keyword, options);
        }
    });
};

// Listen to input and option changes
keywordInput.addEventListener("input", performMark);

console.log($(".table-hover"));

$(".table-hover").mouseover(function() {
    console.log("over a td");
    // window.location = $(this).data("href");
});
