$._originalGetJSON = $.getJSON;

$.expectJSON = function(url, json, delay){
	if ($._JSONcalls === undefined){
		$._JSONcalls = [];
	}
	$._JSONcalls[url] = {
		json: json,
		delay: delay
	};
};

$.getJSON = function(url, options, callback){
	if ($._JSONcalls === undefined){
		$._originalGetJSON(url, options, callback);
		return;
	}
	
	var _fn;
	
	if (options != null && options instanceof Function){
		_fn = options;
	}
	if (callback != null && callback instanceof Function){
		_fn = callback;
	}
	
	if (_fn != null){
		var _delay = 0;
		if ($._JSONcalls[url].delay != null && typeof($._JSONcalls[url].delay*1) == "number"){
			_delay = $._JSONcalls[url].delay*1;
		}
		setTimeout(function(){
			_fn($._JSONcalls[url].json)
		}, _delay);
	}
	
	
};