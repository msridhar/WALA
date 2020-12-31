//function base() {
//    print(7);
//}
//
//function bad() {
//    print("bad");
//    throw 17;
//}
//
//function good() {
//    print("good");
//    return 5;
//}
//
//function oo1() {
//	throw "other one";
//}
//
//function oo2() {
//	throw "other two";
//}

function complexTryFinally(a) {
  try {
    try {
      a.f;
    } finally {
      try {
        a.one();
      } finally {
        oo1();
      }
    }
  } finally {

  }
}

complexTryFinally({f: base, two: good, one: bad})

