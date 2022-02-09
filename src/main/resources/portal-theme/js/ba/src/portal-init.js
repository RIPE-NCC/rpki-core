$(document).ready(function(){
	$(".portletHeader").click(function(){ $(this).next().slideToggle() });
});

function showTourLabel() {
	if ($("#apiLabel").is(':visible')) {
		$("#apiLabel").css("top", "395px");
	}
	return $("#takeTheTourLabel").show();
}

function showApiLabel() {
	if ($("#takeTheTourLabel").is(':visible')) {
		$("#apiLabel").css("top", "395px");
	}
	return $("#apiLabel").show();
}

/*Don't change the function name. User like(Third party) functionality doesn't work of
 call back method name is different*/

function userlikeButtonHandler(visible) {
    if (visible) {
        $("#livechatLabel").show();
        if ($("#apiLabel").is(':visible')) {
            $("#apiLabel").css("top", "395px");
        }
        if ($("#takeTheTourLabel").is(':visible')) {
            $("#takeTheTourLabel").css("top", "395px");
        }
    } else {
        $("#livechatLabel").hide();
    }
}