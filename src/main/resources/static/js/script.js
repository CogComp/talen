/**
 * Created by mayhew2 on 5/31/16.
 */

$(document).ready(function() {

    function savetas(){
        console.log("saving tas...");
        $.ajax({
            url: "/save"
        }).done(function (msg) {
            console.log("finished saving!");
        });

    }

    $( ".saveclass" ).click(savetas);

    $(window).bind('beforeunload', savetas);



});
