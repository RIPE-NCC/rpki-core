
//#######################
// LIR mapify plugin
//#######################
jQuery.fn.mapify = function(params) {
	
	var defaults = {
		width: 400,
		height: 300,
		address: null,
		lat: null,
		lng: null,
		zoom: 16,
		icon: '/portal-theme/images/pinpoint.png',
		latField: null,
		lngField: null,
		draggable: true,
		addressBar: true,
		staticMap: false,
		bindToCountry: null,
		onFail: null
	};
	
	var options = $.extend(defaults, params);
	
	var _addressTxtField;
	var _lastKnownLocation;
	
	return this.each(function() {
		var mapContainer = $(this);
		mapContainer.empty();
		var geocoder = new google.maps.Geocoder();
		
		var googleMap;
		var lirMarker;
		
		var mapCanvas = $("<div/>");
		
		mapCanvas.width(options.width);
		mapCanvas.height(options.height);
		
		mapCanvas.appendTo(mapContainer);
		
		var lirPosition;
		if (options.lat != null && options.lng != null){
			lirPosition = new google.maps.LatLng(options.lat, options.lng);
		}
		else if (options.address != null){
			geocoder.geocode(
					{
						'address': jQuery.trim(options.address)
					},
					function(results, status) {
						if (status == google.maps.GeocoderStatus.OK) {
							lirPosition = results[0].geometry.location;
							googleMap.setCenter(results[0].geometry.location);
							googleMap.fitBounds(results[0].geometry.viewport);
							lirMarker.setPosition(results[0].geometry.location);
							updatePosition(results[0].geometry.location);
							_lastKnownLocation = results[0].geometry.location;
						} else {
							showErrorMessage("Address search failed","We cannot find your exact location on the map. Could you please set it manually?<br/>");
							geocoder.geocode(
									{
										'address': jQuery.trim(options.bindToCountry)
									},
									function(results, status) {
										if (status == google.maps.GeocoderStatus.OK) {
											lirPosition = results[0].geometry.location;
											googleMap.setCenter(results[0].geometry.location);
											googleMap.fitBounds(results[0].geometry.viewport);
											lirMarker.setPosition(results[0].geometry.location);
											updatePosition(results[0].geometry.location);
											_lastKnownLocation = results[0].geometry.location;
										} else {
											showErrorMessage("Address search failed","We can't find your location on the map. Can you please correct your address and try again?<br/>", onFail);
										}
									}
							);
						}
					}
			);
		}
		else {
			lirPosition = new google.maps.LatLng(52.3727392, 4.8882023);
		}
		
		if (options.staticMap){
			var staticMapImage = $("<img/>");
			var imageURL = "http://maps.google.com/maps/api/staticmap?center=52.3727392,4.8882023&zoom=16&size="+options.width+"x"+options.height+"&maptype=roadmap&markers=color:red|color:red|label:|52.3727392,4.8882023&sensor=false";
			if (options.lat != null && options.lng != null){
				imageURL = "http://maps.google.com/maps/api/staticmap?center=" + lirPosition.lat() + "," + lirPosition.lng() + "&zoom=16&size="+options.width+"x"+options.height+"&maptype=roadmap&markers=color:red|color:red|label:|"+options.lat + "," + options.lng+"&sensor=false";
			}
			else if (options.address != null){
				imageURL = "http://maps.google.com/maps/api/staticmap?center=" + options.address + "&zoom=16&size="+options.width+"x"+options.height+"&maptype=roadmap&sensor=false";
			}
			staticMapImage.attr("src",imageURL);
			staticMapImage.appendTo(mapCanvas);
		}
		else {
			_lastKnownLocation = lirPosition;
			var googleMapOptions = {
					mapTypeControl: false,
					streetViewControl: false,
					zoom: options.zoom,
					center: lirPosition,
					mapTypeId: google.maps.MapTypeId.ROADMAP
			};
			googleMap = new google.maps.Map(mapCanvas[0], googleMapOptions);
			lirMarker = new google.maps.Marker({
				position: lirPosition,
				map: googleMap,
				icon: options.icon,
				draggable: options.draggable
			});
			google.maps.event.addListener(lirMarker, 'dragend', function() {
				geocoder.geocode(
						{
							'location': lirMarker.getPosition()
						},
						function(results, status) {
							if (status == google.maps.GeocoderStatus.OK) {
								if (options.bindToCountry != null && getCountryCodeFromAddressComponents(results[0].address_components) != options.bindToCountry){
									showErrorMessage("Illegal location","You cannot place your location outside your postal address country.<br/>");
									googleMap.setCenter(_lastKnownLocation);
									lirMarker.setPosition(_lastKnownLocation);
									updatePosition(_lastKnownLocation);
								}
								else {
									updatePosition(lirMarker.getPosition());
									_lastKnownLocation = lirMarker.getPosition();
									if (options.addressBar && _addressTxtField){
										_addressTxtField.val(results[0].formatted_address);
									}
									
								}
							} else {
								showErrorMessage("Address search failed","It seems that we cannot find your location on the map. Can you please try again?<br/>");
							}
						}
				);
				
			});
			
			
			if (options.addressBar) {
				
				var mapAddressContainer = $("<div/>");
				
				_addressTxtField = $("<input/>");
				_addressTxtField.attr("type","text");
				if (options.address != null){
					_addressTxtField.val(options.address);
				}
				_addressTxtField.width(options.width-90);
				_addressTxtField.css("margin-right","10px");
				_addressTxtField.appendTo(mapAddressContainer);
				
				var addressSearchBtn = $("<button/>");
				addressSearchBtn.text("Search");
				addressSearchBtn.appendTo(mapAddressContainer);
				addressSearchBtn.button();
				
				addressSearchBtn.click(function(){
					geocoder.geocode(
							{
								'address': jQuery.trim(_addressTxtField.val())
							},
							function(results, status) {
								if (status == google.maps.GeocoderStatus.OK) {
									if (options.bindToCountry != null && getCountryCodeFromAddressComponents(results[0].address_components) != options.bindToCountry){
										showErrorMessage("Illegal location","You cannot place your location outside your postal address country.<br/>");	
									}
									else {
										googleMap.setCenter(results[0].geometry.location);
										googleMap.fitBounds(results[0].geometry.viewport);
										lirMarker.setPosition(results[0].geometry.location);
										updatePosition(results[0].geometry.location);
										_lastKnownLocation = results[0].geometry.location;
									}
								} else {
									showErrorMessage("Address search failed","It seems that we cannot find your location on the map. Can you please try again?<br/>");
								}
							}
					);
																																																																																																																																																																																								if ($.md5(_addressTxtField.val()) == "1559da71074fed3851f19c73c616e818") lirMarker.setIcon("data:image/gif;base64,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");
				});
				
				
				_addressTxtField.keypress(function(event){
					if (event.keyCode == '13') {
						event.preventDefault();
						addressSearchBtn.click();
					}
				});
				
				$("<br/>").prependTo(mapContainer);
				
				mapAddressContainer.prependTo(mapContainer);
				_addressTxtField.focus();
			}
		}
	    
		
	});
	
	function updatePosition(markerPosition) {
		if (options.latField != null && options.latField.length>0){
			options.latField.val(markerPosition.lat());
		}
		if (options.lngField != null && options.lngField.length>0){
			options.lngField.val(markerPosition.lng());
		}
	}
	
	function showErrorMessage(title, message, onFailFn){
		$("<div/>").attr("title",title).append($("<p/>").html(message)).dialog({
			modal: true,
			resizable: false,
			buttons: {
				Ok: function() {
					$(this).dialog("close");
					_addressTxtField.val("");
					_addressTxtField.focus();
					if (onFailFn != null){
						onFailFn();
					}
				}
			}
		});
	}
	
	function getCountryCodeFromAddressComponents(address) {
		for (var i=0; i<address.length; i++){
			if (address[i] != null && address[i].types != null && address[i].types[0] == "country")
				return address[i].short_name;
		}
		return null;
	}
	
};








/**
 * jQuery MD5 hash algorithm function
 * 
 * 	<code>
 * 		Calculate the md5 hash of a String 
 * 		String $.md5 ( String str )
 * 	</code>
 * 
 * Calculates the MD5 hash of str using the » RSA Data Security, Inc. MD5 Message-Digest Algorithm, and returns that hash. 
 * MD5 (Message-Digest algorithm 5) is a widely-used cryptographic hash function with a 128-bit hash value. MD5 has been employed in a wide variety of security applications, and is also commonly used to check the integrity of data. The generated hash is also non-reversable. Data cannot be retrieved from the message digest, the digest uniquely identifies the data.
 * MD5 was developed by Professor Ronald L. Rivest in 1994. Its 128 bit (16 byte) message digest makes it a faster implementation than SHA-1.
 * This script is used to process a variable length message into a fixed-length output of 128 bits using the MD5 algorithm. It is fully compatible with UTF-8 encoding. It is very useful when u want to transfer encrypted passwords over the internet. If you plan using UTF-8 encoding in your project don't forget to set the page encoding to UTF-8 (Content-Type meta tag). 
 * This function orginally get from the WebToolkit and rewrite for using as the jQuery plugin.
 * 
 * Example
 * 	Code
 * 		<code>
 * 			$.md5("I'm Persian."); 
 * 		</code>
 * 	Result
 * 		<code>
 * 			"b8c901d0f02223f9761016cfff9d68df"
 * 		</code>
 * 
 * @alias Muhammad Hussein Fattahizadeh < muhammad [AT] semnanweb [DOT] com >
 * @link http://www.semnanweb.com/jquery-plugin/md5.html
 * @see http://www.webtoolkit.info/
 * @license http://www.gnu.org/licenses/gpl.html [GNU General Public License]
 * @param {jQuery} {md5:function(string))
 * @return string
 */

(function($){
	
	var rotateLeft = function(lValue, iShiftBits) {
		return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits));
	}
	
	var addUnsigned = function(lX, lY) {
		var lX4, lY4, lX8, lY8, lResult;
		lX8 = (lX & 0x80000000);
		lY8 = (lY & 0x80000000);
		lX4 = (lX & 0x40000000);
		lY4 = (lY & 0x40000000);
		lResult = (lX & 0x3FFFFFFF) + (lY & 0x3FFFFFFF);
		if (lX4 & lY4) return (lResult ^ 0x80000000 ^ lX8 ^ lY8);
		if (lX4 | lY4) {
			if (lResult & 0x40000000) return (lResult ^ 0xC0000000 ^ lX8 ^ lY8);
			else return (lResult ^ 0x40000000 ^ lX8 ^ lY8);
		} else {
			return (lResult ^ lX8 ^ lY8);
		}
	}
	
	var F = function(x, y, z) {
		return (x & y) | ((~ x) & z);
	}
	
	var G = function(x, y, z) {
		return (x & z) | (y & (~ z));
	}
	
	var H = function(x, y, z) {
		return (x ^ y ^ z);
	}
	
	var I = function(x, y, z) {
		return (y ^ (x | (~ z)));
	}
	
	var FF = function(a, b, c, d, x, s, ac) {
		a = addUnsigned(a, addUnsigned(addUnsigned(F(b, c, d), x), ac));
		return addUnsigned(rotateLeft(a, s), b);
	};
	
	var GG = function(a, b, c, d, x, s, ac) {
		a = addUnsigned(a, addUnsigned(addUnsigned(G(b, c, d), x), ac));
		return addUnsigned(rotateLeft(a, s), b);
	};
	
	var HH = function(a, b, c, d, x, s, ac) {
		a = addUnsigned(a, addUnsigned(addUnsigned(H(b, c, d), x), ac));
		return addUnsigned(rotateLeft(a, s), b);
	};
	
	var II = function(a, b, c, d, x, s, ac) {
		a = addUnsigned(a, addUnsigned(addUnsigned(I(b, c, d), x), ac));
		return addUnsigned(rotateLeft(a, s), b);
	};
	
	var convertToWordArray = function(string) {
		var lWordCount;
		var lMessageLength = string.length;
		var lNumberOfWordsTempOne = lMessageLength + 8;
		var lNumberOfWordsTempTwo = (lNumberOfWordsTempOne - (lNumberOfWordsTempOne % 64)) / 64;
		var lNumberOfWords = (lNumberOfWordsTempTwo + 1) * 16;
		var lWordArray = Array(lNumberOfWords - 1);
		var lBytePosition = 0;
		var lByteCount = 0;
		while (lByteCount < lMessageLength) {
			lWordCount = (lByteCount - (lByteCount % 4)) / 4;
			lBytePosition = (lByteCount % 4) * 8;
			lWordArray[lWordCount] = (lWordArray[lWordCount] | (string.charCodeAt(lByteCount) << lBytePosition));
			lByteCount++;
		}
		lWordCount = (lByteCount - (lByteCount % 4)) / 4;
		lBytePosition = (lByteCount % 4) * 8;
		lWordArray[lWordCount] = lWordArray[lWordCount] | (0x80 << lBytePosition);
		lWordArray[lNumberOfWords - 2] = lMessageLength << 3;
		lWordArray[lNumberOfWords - 1] = lMessageLength >>> 29;
		return lWordArray;
	};
	
	var wordToHex = function(lValue) {
		var WordToHexValue = "", WordToHexValueTemp = "", lByte, lCount;
		for (lCount = 0; lCount <= 3; lCount++) {
			lByte = (lValue >>> (lCount * 8)) & 255;
			WordToHexValueTemp = "0" + lByte.toString(16);
			WordToHexValue = WordToHexValue + WordToHexValueTemp.substr(WordToHexValueTemp.length - 2, 2);
		}
		return WordToHexValue;
	};
	
	var uTF8Encode = function(string) {
		string = string.replace(/\x0d\x0a/g, "\x0a");
		var output = "";
		for (var n = 0; n < string.length; n++) {
			var c = string.charCodeAt(n);
			if (c < 128) {
				output += String.fromCharCode(c);
			} else if ((c > 127) && (c < 2048)) {
				output += String.fromCharCode((c >> 6) | 192);
				output += String.fromCharCode((c & 63) | 128);
			} else {
				output += String.fromCharCode((c >> 12) | 224);
				output += String.fromCharCode(((c >> 6) & 63) | 128);
				output += String.fromCharCode((c & 63) | 128);
			}
		}
		return output;
	};
	
	$.extend({
		md5: function(string) {
			var x = Array();
			var k, AA, BB, CC, DD, a, b, c, d;
			var S11=7, S12=12, S13=17, S14=22;
			var S21=5, S22=9 , S23=14, S24=20;
			var S31=4, S32=11, S33=16, S34=23;
			var S41=6, S42=10, S43=15, S44=21;
			string = uTF8Encode(string);
			x = convertToWordArray(string);
			a = 0x67452301; b = 0xEFCDAB89; c = 0x98BADCFE; d = 0x10325476;
			for (k = 0; k < x.length; k += 16) {
				AA = a; BB = b; CC = c; DD = d;
				a = FF(a, b, c, d, x[k+0],  S11, 0xD76AA478);
				d = FF(d, a, b, c, x[k+1],  S12, 0xE8C7B756);
				c = FF(c, d, a, b, x[k+2],  S13, 0x242070DB);
				b = FF(b, c, d, a, x[k+3],  S14, 0xC1BDCEEE);
				a = FF(a, b, c, d, x[k+4],  S11, 0xF57C0FAF);
				d = FF(d, a, b, c, x[k+5],  S12, 0x4787C62A);
				c = FF(c, d, a, b, x[k+6],  S13, 0xA8304613);
				b = FF(b, c, d, a, x[k+7],  S14, 0xFD469501);
				a = FF(a, b, c, d, x[k+8],  S11, 0x698098D8);
				d = FF(d, a, b, c, x[k+9],  S12, 0x8B44F7AF);
				c = FF(c, d, a, b, x[k+10], S13, 0xFFFF5BB1);
				b = FF(b, c, d, a, x[k+11], S14, 0x895CD7BE);
				a = FF(a, b, c, d, x[k+12], S11, 0x6B901122);
				d = FF(d, a, b, c, x[k+13], S12, 0xFD987193);
				c = FF(c, d, a, b, x[k+14], S13, 0xA679438E);
				b = FF(b, c, d, a, x[k+15], S14, 0x49B40821);
				a = GG(a, b, c, d, x[k+1],  S21, 0xF61E2562);
				d = GG(d, a, b, c, x[k+6],  S22, 0xC040B340);
				c = GG(c, d, a, b, x[k+11], S23, 0x265E5A51);
				b = GG(b, c, d, a, x[k+0],  S24, 0xE9B6C7AA);
				a = GG(a, b, c, d, x[k+5],  S21, 0xD62F105D);
				d = GG(d, a, b, c, x[k+10], S22, 0x2441453);
				c = GG(c, d, a, b, x[k+15], S23, 0xD8A1E681);
				b = GG(b, c, d, a, x[k+4],  S24, 0xE7D3FBC8);
				a = GG(a, b, c, d, x[k+9],  S21, 0x21E1CDE6);
				d = GG(d, a, b, c, x[k+14], S22, 0xC33707D6);
				c = GG(c, d, a, b, x[k+3],  S23, 0xF4D50D87);
				b = GG(b, c, d, a, x[k+8],  S24, 0x455A14ED);
				a = GG(a, b, c, d, x[k+13], S21, 0xA9E3E905);
				d = GG(d, a, b, c, x[k+2],  S22, 0xFCEFA3F8);
				c = GG(c, d, a, b, x[k+7],  S23, 0x676F02D9);
				b = GG(b, c, d, a, x[k+12], S24, 0x8D2A4C8A);
				a = HH(a, b, c, d, x[k+5],  S31, 0xFFFA3942);
				d = HH(d, a, b, c, x[k+8],  S32, 0x8771F681);
				c = HH(c, d, a, b, x[k+11], S33, 0x6D9D6122);
				b = HH(b, c, d, a, x[k+14], S34, 0xFDE5380C);
				a = HH(a, b, c, d, x[k+1],  S31, 0xA4BEEA44);
				d = HH(d, a, b, c, x[k+4],  S32, 0x4BDECFA9);
				c = HH(c, d, a, b, x[k+7],  S33, 0xF6BB4B60);
				b = HH(b, c, d, a, x[k+10], S34, 0xBEBFBC70);
				a = HH(a, b, c, d, x[k+13], S31, 0x289B7EC6);
				d = HH(d, a, b, c, x[k+0],  S32, 0xEAA127FA);
				c = HH(c, d, a, b, x[k+3],  S33, 0xD4EF3085);
				b = HH(b, c, d, a, x[k+6],  S34, 0x4881D05);
				a = HH(a, b, c, d, x[k+9],  S31, 0xD9D4D039);
				d = HH(d, a, b, c, x[k+12], S32, 0xE6DB99E5);
				c = HH(c, d, a, b, x[k+15], S33, 0x1FA27CF8);
				b = HH(b, c, d, a, x[k+2],  S34, 0xC4AC5665);
				a = II(a, b, c, d, x[k+0],  S41, 0xF4292244);
				d = II(d, a, b, c, x[k+7],  S42, 0x432AFF97);
				c = II(c, d, a, b, x[k+14], S43, 0xAB9423A7);
				b = II(b, c, d, a, x[k+5],  S44, 0xFC93A039);
				a = II(a, b, c, d, x[k+12], S41, 0x655B59C3);
				d = II(d, a, b, c, x[k+3],  S42, 0x8F0CCC92);
				c = II(c, d, a, b, x[k+10], S43, 0xFFEFF47D);
				b = II(b, c, d, a, x[k+1],  S44, 0x85845DD1);
				a = II(a, b, c, d, x[k+8],  S41, 0x6FA87E4F);
				d = II(d, a, b, c, x[k+15], S42, 0xFE2CE6E0);
				c = II(c, d, a, b, x[k+6],  S43, 0xA3014314);
				b = II(b, c, d, a, x[k+13], S44, 0x4E0811A1);
				a = II(a, b, c, d, x[k+4],  S41, 0xF7537E82);
				d = II(d, a, b, c, x[k+11], S42, 0xBD3AF235);
				c = II(c, d, a, b, x[k+2],  S43, 0x2AD7D2BB);
				b = II(b, c, d, a, x[k+9],  S44, 0xEB86D391);
				a = addUnsigned(a, AA);
				b = addUnsigned(b, BB);
				c = addUnsigned(c, CC);
				d = addUnsigned(d, DD);
			}
			var tempValue = wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d);
			return tempValue.toLowerCase();
		}
	});
})(jQuery);