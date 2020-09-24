// IIFE, named function
(function f1() {})();

// IIFE, anon function
(function () {})();

// parameters and returns
function id(p) {
  return p;
}

function callId() {
  var x = {};
  var y = id(x);
}

function callIdHigherOrder() {
  var fp = id;
  var x = {};
  var y = fp(x);
}

function voidFun() {
  return;
}

voidFun();

// lexical variables
(function () {
  function main(){
    var x = function() { return 2; }
    var y = function() { return x();}// interesting call site
    y();
  }
  main();
})();