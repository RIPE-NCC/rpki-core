/*
 * Certification related JavaScript code.
 *
 * Requires at least jQuery, data tables 1.9.2, and underscore.js.
 */

/*
 * We need the following plugin by Pedro Alves to dynamically change the nr of records we display
 * http://datatables.net/plug-ins/api#fnLengthChange
 */

$.fn.dataTableExt.oApi.fnLengthChange = function ( oSettings, iDisplay )
{
    oSettings._iDisplayLength = iDisplay;
    oSettings.oApi._fnCalculateEnd( oSettings );
      
    /* If we have space to show extra rows (backing up from the end point - then do so */
    if ( oSettings._iDisplayEnd == oSettings.aiDisplay.length )
    {
        oSettings._iDisplayStart = oSettings._iDisplayEnd - oSettings._iDisplayLength;
        if ( oSettings._iDisplayStart < 0 )
        {
            oSettings._iDisplayStart = 0;
        }
    }
      
    if ( oSettings._iDisplayLength == -1 )
    {
        oSettings._iDisplayStart = 0;
    }
      
    oSettings.oApi._fnDraw( oSettings );
      
    if ( oSettings.aanFeatures.l )
    {
        $('select', oSettings.aanFeatures.l).val( iDisplay );
    }
};


var certification = (function() {
    var result = {};

    /*
     * Log errors to the server.
     */
    result.logError = function(details) {
        $.ajax({
            type : 'POST',
            url : '/certification/log-client-errors',
            data : JSON.stringify({
                context : navigator.userAgent,
                details : details
            }),
            contentType : 'application/json; charset=utf-8'
        });
    };

    /*
     * Detect IE version (see http://stackoverflow.com/a/5574871).
     */
    result.ie = (function(){
        var undef,
            v = 3,
            div = document.createElement('div'),
            all = div.getElementsByTagName('i');
        while (
            div.innerHTML = '<!--[if gt IE ' + (++v) + ']><i></i><![endif]-->',
            all[0]
        );
        return v > 4 ? v : undef;
    }());

    /*
     * Function to turn a table with certification styling into a data table,
     * while binding the filters, search, pagination, etc. correctly.
     *
     * The table must be properly wrapped in a div and the (jQuery) selector
     * should match this div. See BgpAnnouncementsPanel.html for an example.
     */
    result.tablelize = function(selector, dataTableOptions) {
        var div = $(selector)
        var searchInput = div.find("input.search");
        var nrRecordsInput = div.find(".nrRecords");
        var filterStates = div.find(".headerNav li.filter");

        $.extend($.fn.dataTableExt.oStdClasses, {
            // Can't disable the datatable pagination info since it stops
            // calling the fnInfoCallback in that case. Hide it using CSS
            // instead.
            "sInfo" : "hide",
        });
        var settings = {
            "sDom" : 'rti',
            "bPaginate" : true,
            "sPaginationType": "full_numbers",
            "bLengthChange" : true,
            "fnInfoCallback" : function(oSettings, iStart, iEnd, iMax, iTotal, sPre) {
                div.find(".paginationInfo").text(sPre);
                div.find(".nrPages").text("of " + Math.ceil(iTotal / oSettings._iDisplayLength))
                div.find("input.paginationPage").val(Math.floor(iStart / oSettings._iDisplayLength) + 1);
                return sPre;
            },
            "fnStateLoaded": function(oSettings, oData) {
                var filter = oData.oSearch.sSearch;
                if (typeof filter !== "undefined") {
                    var words = filter.split(/\s+/)
                    _.forEach(filterStates, function (filterToggle) {
                        var filterState = $(filterToggle).attr("data-filter");
                        if (_.include(words, filterState)) {
                            filterStates.removeClass("active");
                            $(filterToggle).addClass("active");
                            words = _.without(words, filterState)
                        }
                    });
                    searchInput.val(words.join(' '));
                }
            }
        };
        $.extend(true, settings, dataTableOptions);
        var table = div.find("table.data").dataTable(settings);
        var updateFilter = function() {
            var search = searchInput.val();
            var activeFilters = _.map(filterStates.filter(".active"), function (e) { return $(e).attr("data-filter") })
            var states = activeFilters.join(" ");
            table.fnFilter(states + " " + search);
        };
        searchInput.bind('keyup blur', _.debounce(updateFilter, 250));

        var updateNrRecords = function() {
            var nr = parseInt(nrRecordsInput.val());
            table.fnLengthChange(nr);
            table.fnDraw();
        }

        nrRecordsInput.change(_.debounce(updateNrRecords, 250));

        filterStates.click(function(e) {
            e.preventDefault();
            filterStates.removeClass("active");
            $(this).addClass('active');
            updateFilter();
        });
        div.find(".paginationAction").click(function(e) {
            e.preventDefault();
            table.fnPageChange($(this).attr("data-action"));
        });
        div.find(".paginationPage").bind('keyup blur', _.debounce(function() {
            var page = parseInt($(this).val(), 10) - 1;
            if (!isNaN(page)) {
                table.fnPageChange(page);
            }
        }, 250))

        return div;
    };

    return result;
})();
