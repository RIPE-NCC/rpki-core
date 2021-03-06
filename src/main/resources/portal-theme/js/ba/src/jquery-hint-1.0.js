
//#######################
// HINT INPUT 
//#######################
$.fn.hint = function(options) {
	
	var defaults = {
		firstValue : ""	
	};
	
	var options = $.extend(defaults, options);
	
	return this.each(function() {
		
		var inp = $(this);
		if (inp.attr("hint") != null && inp.attr("hint") != undefined && jQuery.trim(inp.val()) == ""){
			inp.addClass("hint").val(inp.attr("hint"));
		}
		inp.click(function(){
			if ($(this).val().toLowerCase() == inp.attr("hint").toLowerCase()){
				inp.val("").removeClass("hint");
				if (options.firstValue != ""){
					inp.val(options.firstValue);
				}
			}
		});
		
		inp.blur(function(){
			if (jQuery.trim($(this).val()) == ""){
				inp.addClass("hint").val(inp.attr("hint"));
			}
		});
		
	});
	
};

