function getCaretPosition() {
    if (window.getSelection && window.getSelection().getRangeAt) {
        var range = window.getSelection().getRangeAt(0);
        var selectedObj = window.getSelection();
        var rangeCount = 0;
        var childNodes = selectedObj.anchorNode.parentNode.childNodes;
        for (var i = 0; i < childNodes.length; i++) {
            if (childNodes[i] === selectedObj.anchorNode) {
                break;
            }
            if (childNodes[i].outerHTML)
                rangeCount += childNodes[i].outerHTML.length;
            else if (childNodes[i].nodeType === 3) {
                rangeCount += childNodes[i].textContent.length;
            }
        }
        return range.startOffset + rangeCount;
    }
    return -1;
}

function placeCaretAtEnd(el) {
    el.focus();
    if (typeof window.getSelection != "undefined"
        && typeof document.createRange != "undefined") {
        var range = document.createRange();
        range.selectNodeContents(el);
        var rLength = range.length;
        range.collapse(false);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        sel.setPosition(el);
    } else if (typeof document.body.createTextRange != "undefined") {
        var textRange = document.body.createTextRange();
        textRange.moveToElementText(el);
        textRange.collapse(false);
        textRange.select();
    }
}

(function ($) {

    $(function () {

        // editable table
        var $TABLE = $('#ingredients');
        var $BTN = $('#export-btn');
        var $MAKEEDITABLE = $('#make-editable');
        var $CANCEL_BTN = $('#table-cancel');

        $MAKEEDITABLE.click(function () {
            $('.editable').attr("contenteditable", true);
            $('.controls').removeClass("hide");
            $MAKEEDITABLE.addClass("never-show");
            $BTN.removeClass("hide");
            $CANCEL_BTN.removeClass("hide");
        });

        $('.table-add').click(function () {
            var $clone = $TABLE.find('tr.hide:not(.header)').clone(true).removeClass('hide table-line');
            var $row = $(this).parents('tr');
            $row.after($clone);
            $clone.firstChild().focus()
        });

        $('.table-remove').click(function () {
            var $row = $(this).parents('tr');
            if ($row.index() === 1 && $row.next().hasClass("hide")) return;
            $row.detach();
        });

        $('.table-up').click(function () {
            var $row = $(this).parents('tr');
            if ($row.index() === 1) return; // Don't go above the header
            $row.prev().before($row.get(0));
        });

        $('.table-down').click(function () {
            var $row = $(this).parents('tr');
            $row.next().after($row.get(0));
        });

        $CANCEL_BTN.click(function () {
            location.reload()
        });

// A few jQuery helpers for exporting only
        jQuery.fn.pop = [].pop;
        jQuery.fn.shift = [].shift;

        $BTN.click(function () {
            var $rows = $TABLE.find('tr.header, tr:not(:hidden)');
            var headers = [];
            var data = [];

            // Get the headers (add special header logic here)
            $($rows.shift()).find('th:not(:empty)').each(function () {
                headers.push($(this).text().toLowerCase());
            });

            // Turn all existing rows into a loopable array
            $rows.each(function () {
                var $td = $(this).find('td.exportable');
                var h = {};

                // Use the headers from earlier to name our hash keys
                headers.forEach(function (header, i) {
                    h[header] = $td.eq(i).text();
                });

                data.push(h);
            });

            // Output the result

            var xhr = new XMLHttpRequest();
            xhr.open("POST", window.location.href + "/ingredients", true);
            xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');

            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        location.reload(true);
                        // $('.editable').attr("contenteditable", false);
                        // $('.controls').addClass("hide");
                        // $MAKEEDITABLE.removeClass("hide");
                        // $BTN.addClass("hide");
                        // $CANCEL_BTN.addClass("hide");

                    } else {
                        alert("Save failed with status: " + xhr.status)
                    }

                }
            };

            xhr.send(JSON.stringify(data));

        });


        // editable list

        var $MAKE_LIST_EDITABLE = $('#make-list-editable');
        var $CANCEL_LIST_BTN = $('#list-cancel');
        var $SAVE_LIST_BTN = $('#list-save');

        var $LIST = $('.steps');

        $MAKE_LIST_EDITABLE.click(function () {
            $('.steps .step p').attr("contenteditable", true);
            $MAKE_LIST_EDITABLE.addClass("never-show");
            $SAVE_LIST_BTN.removeClass("hide");
            $CANCEL_LIST_BTN.removeClass("hide");
        });

        $CANCEL_LIST_BTN.click(function () {
            location.reload()
        });

        $SAVE_LIST_BTN.click(function () {
            var $list = $LIST.find('.step:not(#template)');
            var data = [];

            // Turn all existing rows into a loopable array
            $list.each(function () {
                var $p = $(this).find('p');
                var $img = $(this).find('.step-image img');
                var h = {};

                // Use the headers from earlier to name our hash keys
                h["text"] = $p.text();
                h["url"] = $img.attr("src");

                data.push(h);
            });

            // Output the result

            var xhr = new XMLHttpRequest();
            xhr.open("POST", window.location.href + "/instructions", true);
            xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');

            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        location.reload(true);
                    } else {
                        alert("Save failed with status: " + xhr.status)
                    }

                }
            };

            xhr.send(JSON.stringify(data));

        });

        $('.steps .step p').keydown(function (event) {
            if (event.which === 13) {
                var $caret = getCaretPosition();
                event.preventDefault();
                var $length = $(this).text().length;

                if ($caret === $length) {
                    var $clone = $LIST.find('li#template').clone(true);
                    $clone.removeAttr("id");
                    var $row = $(this).parents('li');
                    $row.after($clone);
                    $clone.find('p').focus();
                }

            } else if ((event.which === 8)) {
                var $caret = getCaretPosition();
                if ($caret === 0 || $(this).text().length === 0) {
                    event.preventDefault();
                    var $curText = $(this).text();
                    var $row = $(this).parents('li');
                    var $prev = $row.prev();
                    if ($prev != null) {
                        if ($row.index() === 1 && $row.next().hasClass("hide")) return;
                        $row.detach();
                        var $p = $prev.find('p');
                        var $prevText = $p.text();
                        $p.text($prevText + $curText);
                        // $p.caretTo($prevText.length);
                        placeCaretAtEnd($p.get(0));
                    }
                }
            }
        });

        // $('.editable').onkeypress(function (event) {
        //     var keyCode = event.which
        //     if (keyCode === 8 || (keyCode >= 35 && keyCode <= 40)) { // Left / Up / Right / Down Arrow, Backspace, Delete keys
        //         return;
        //     }
        //     var regex = new RegExp("^[a-zA-Z0-9^°]+$");
        //     var key = String.fromCharCode(event.which);
        //     if (!regex.test(key)) {
        //         event.preventDefault();
        //     }
        // });

        $('#editable-title').keydown(function (event) {
            var $this = $('#editable-title');
            if (event.which === 13) {
                event.preventDefault();
                $this.blur();

                $('.spinner').fadeIn();

                var xhr = new XMLHttpRequest();
                xhr.open("POST", window.location.href + "/title");
                xhr.setRequestHeader('Content-Type', 'application/json; charset=utf-8');

                xhr.onreadystatechange = function () {
                    if (xhr.readyState === 4) {
                        if (xhr.status === 200) {
                            window.open(xhr.responseURL, "_self")
                        } else {
                            alert("Updating the title failed with status: " + xhr.status)
                        }
                    }
                };

                xhr.send(JSON.stringify({title: $this.text()}));
            }
        });

        window.onload = function (ev) {
            var $title = $("#editable-title");
            if ($title.length > 0) {
                if ($title.attr("autofocus") != null) {
                    $title.focus();
                }
            }

            $(".gallery .content .media img").each(function () {
                if ($(this).width() > $(this).height()) {
                    $(this).addClass("landscape");
                    $(this).removeClass("portrait");
                } else if ($(this).width() < $(this).height()) {
                    $(this).addClass("portrait");
                    $(this).removeClass("landscape");
                }
            })

            $('spinner').fadeOut()

        };

        $("#image-upload").change(function () {
            $('.spinner').fadeIn();
            $("#image-upload-form").submit();
        });
    });

    $('a').click(function () {
        $('.spinner').fadeIn();
    })

})(jQuery);
//
// document.getElementById("image-upload").onchange = function () {
//     document.getElementById("image-upload-form").submit();
// };
//
// document.getElementById("autofocus").onload = function () {
//     document.getElementById("autofocus").focus();
// };