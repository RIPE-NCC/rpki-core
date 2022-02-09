var glossary = new Array();

$(document).ready(function () {
		$("#help").dialog({
			autoOpen: false,
			closeOnEscape: true,
			resizable: false,
			modal: false,
			buttons: {
			}
		});
		
	    $.ajax({
	        type: "GET",
	        url: "/glossary/glossary.xml",
	        dataType: "xml",
	        success: xmlParser
	    });
		    
		function xmlParser(xml) {
			$(xml).find('term').each(function(){
				var glossaryObject = {};
				glossaryObject.id = $(this).attr("id");
				glossaryObject.label = $(this).find("label").text();
				glossaryObject.desc = jQuery.trim($(this).find("fullDescription").text());
				glossary[glossaryObject.id] = glossaryObject;
			});
			$("span[termId]").each(function(){
				var term = glossary[$(this).attr("termId")];
				$(this).qtip({
                    hide: {
                        delay: 500,
                        fixed: true
                    },
					content: {
						text: term.desc,
						title: {
							text: term.label
						}
					},
					position: {
						my: "bottom left",
						at: "top right"
					}
				});
			});
		}

});