/**
 * Created by stephen on 8/30/17.
 */


$(document).ready(function() {
    console.log("loading stuff...");

    var highlighting = false;
    var range = {start:-1, end:-1}

    function resetrange(){
        range = {start:-1, end:-1};
        highlightrange();
    }

    function highlightrange(){

        $(".highlighted").removeClass("highlighted");
        $(".highlightstart").removeClass("highlightstart");
        $(".highlightend").removeClass("highlightend");
        if(range.start == -1 && range.end == -1) {
            // do nothing.
        }else{
            for(var i = range.start; i <= range.end; i++){
                if(i == range.start){
                    $("#tok-" + i).addClass("highlightstart");    
                }
                else if(i == range.end){
                    $("#tok-" + i).addClass("highlightend");
                }else{
                    $("#tok-" + i).addClass("highlighted");
                }
            }
        }
    }

    /** This returns true if the range has been changed. **/
    function updaterange(id){
        var intid = parseInt(id.split("-")[1]);
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


    function loadtok(){
        console.log("loadtok called...");

        $(document).mouseup(function(event){
            highlighting = false;
        });


        $(function () {
            $('[data-toggle="tooltip"]').tooltip()
        })


        // just clean everything out first...


        $("[id^=tok]").popover({
            placement: "bottom",
            content: function () {
                var html = $("#buttons").html();
                var out = " <div id='popover" + $(this)[0].id + "'>" + html + "</div>";
                return out;
            },
            title: function () {
                var t = $(this)[0];
                return "Labeled: " + t.className + ", id: " + t.id;
            },
            html: true,
            trigger: "focus",
            container: $(".text")
        });

        //$("[id^=tok]").off("mouseup");

        $("[id^=tok]").mouseover(function(event){

            if(highlighting && updaterange(this.id)) {
                //$(this).addClass("highlighted");
            }
        });

        // click on anything but a letter, and they hide.
        $(document).mousedown(function(e){

            var hasclass = $(e.target).hasClass("labelbutton");

            if(!e.target.id.startsWith("tok") && !hasclass) {
                console.log("extraneous click");
                $("[id^=tok]").popover("hide");
                resetrange();
            }
        });

        $("[id^=tok]").mouseup(function(event){

//                    $(this).removeClass("highlighted");

            console.log(event.which);

            // only toggle on the left click.
            if(event.which == 1) {
                $("[id^=tok]").not($(this)).popover('hide');
                $(this).popover("toggle");
            }

            console.log("mouseup");

            highlighting = false;

        });

        $("[id^=tok]").mousedown(function(event){
            console.log($(this));
            highlighting = true;
            resetrange();
            updaterange(this.id);
        });

        $("div.text")
            .mouseenter(function(){})
            .mouseleave(function(){
                highlighting = false;
            })


        // on right click, change label to nothing.
        $("[id^=tok]").contextmenu(function(event){
            event.preventDefault();
            var span = event.currentTarget;
            console.log("Right clicked on " + span.id);

            removelabel(span);
        });
    }

    loadtok();

    $("#dictbutton").click(function(){
        // ideal: store definition as part of the span, just switch between attributes.
        console.log("Do it locally!")
    });

    $("#loadtokbutton").click(function(){
        loadtok();
    });

    // this runs when you click on a single button.
    $("body").on("click", '.popover button', function(event){
        var buttonvalue = $(this)[0].value;

        //var spanid = $(this).parents("[id^=popovertok]")[0].id;

        console.log(event);

        console.log($(this).parents());

        var sentid = $(this).parents(".card-body")[0].id;


        console.log(sentid);
        console.log("Clicked on a button..." + buttonvalue);
        console.log(range);

        var startid = "tok-" + range.start;
        var endid = "tok-" + (range.end+1);

        $("[id^=tok]").popover("hide");
        resetrange();

        addlabel(sentid, startid, endid, buttonvalue);
    });


    function getParameterByName(name) {
        var match = RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
        return match && decodeURIComponent(match[1].replace(/\+/g, ' '));
    }

    // given a span id (e.g. tok-4), return the integer
    function getnum(spanid){
        return parseInt(spanid.split("-").slice(-1)[0]);
    }


    // run this function when you click on a token.
    function addlabel(sentid, starttokid, endtokid, newclass) {
        console.log("Adding " + newclass + " to " + starttokid + "---" + endtokid + ", sentid=" + sentid);

        if(newclass == "O"){
            return;
        }

        // here, instead of going to the serer, we just update the javascript immediately.
        var cardtext = $("#" + sentid);
        var toks = cardtext.find("[id^='tok']");
        var spanslabels = getspanslabels(cardtext);

        var newspanlabel = newclass + "-" + getnum(starttokid) + "-" + getnum(endtokid);
        spanslabels.push(newspanlabel);
        var newhtml = producetext(toks, spanslabels);

        cardtext.html(newhtml);
        loadtok();
    };


    // Given a span, remove the label.
    function removelabel(span) {

        $("#savebutton").html("<span class=\"glyphicon glyphicon-floppy-disk\" aria-hidden=\"true\"></span> Save");
        $("#savebutton").css({"border-color" : "#c00"});

        var sentid = $(span).parents(".card-body")[0].id;

        // returns just the number of the token. tok-4, returns 4.
        var tokid = getnum(span.id);

        console.log("Removing label from: " + sentid + ":" + tokid);
        console.log("remove locally!!");
    }

    function getspanslabels(htmlstring){
        // this function will: take a current string, abstract spans and labels
        var cons = $(htmlstring).find("[id^='cons']")
        console.log(cons);
        var ret = $.map(cons, function(c){
            var rets = "";
            var label = c.className.replace("pointer", "").replace("cons", "").trim();
            rets += label;
            var idarr = c.id.split("-")
            rets += "-";
            rets += idarr[1] + "-";
            rets += idarr[2];
            return rets;
        })
        return ret;
    }

    function producetext(toks, spanslabels){
        // this will: create a new html string with additional spans and labels added or removed.

        var stoks = $.map(toks, function(c){
            return c.outerHTML;
        });

        $.each(spanslabels, function(i,d){
            var sd = d.split("-");
            var label = sd[0];
            var start = sd[1];
            var end = sd[2];
            var newid = d.replace(label, "cons");
            var template = "<span class='LAB pointer cons' id='ID'>".replace("LAB",label).replace("ID", newid);

            stoks[start] =  template + stoks[start];
            stoks[end-1] += "</span>";
        });

        return stoks.join("");
    }
});