var classes = []; //list of your classes
for (var h = 0; h < 42; h++ ) {
    classes.push("highlight"+h)
}
var elms = {};
var n = {}, nclasses = classes.length;

function changeColor(classname, color) {
    var curN = n[classname];
    for (var i = 0; i < curN; i++) {
        elms[classname][i].style.backgroundColor = color;
    }
}

for (var k = 0; k < nclasses; k++) {
    var curClass = classes[k];
    elms[curClass] = document.getElementsByClassName(curClass);
    n[curClass] = elms[curClass].length;
    var curN = n[curClass];
    for (var i = 0; i < curN; i++) {
        elms[curClass][i].onmouseover = function () {
            changeColor(this.classList[0], "var(--accent-color)");
        };
        elms[curClass][i].onmouseout = function () {
            changeColor(this.classList[0], "transparent");
        };
    }
}
;