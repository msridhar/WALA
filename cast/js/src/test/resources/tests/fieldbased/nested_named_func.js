function foo() {
  function bar() {}
}

function foo2() {
  var bar2 = function bar2() {};
}

function foo3() {
  var bar3 = function() {};
}

foo();
foo2();
foo3();
var x = new String("hi");