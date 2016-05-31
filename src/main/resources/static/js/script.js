/**
 * Created by mayhew2 on 5/31/16.
 */

$(document).ready(function() {

    $(window).bind('beforeunload', function(){
        
        console.log("saving tas...");
        $.ajax({
            url: "/save"
        }).done(function (msg) {
            console.log("finished saving!");
        });
        
    });





});
