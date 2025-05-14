package com.ibm.wala.types.generics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link MethodTypeSignature}. */
class MethodTypeSignatureTest {

  @Test
  void allPrimitives() {
    MethodTypeSignature sig = MethodTypeSignature.make("(BCDFIJSZ)V");
    assertThat(
        sig.getArguments(),
        arrayContaining(
            is(BaseType.BYTE),
            is(BaseType.CHAR),
            is(BaseType.DOUBLE),
            is(BaseType.FLOAT),
            is(BaseType.INT),
            is(BaseType.LONG),
            is(BaseType.SHORT),
            is(BaseType.BOOLEAN)));
    assertThat(sig.getReturnType(), is(BaseType.VOID));
  }

  @Test
  void simpleReferenceTypes() {
    MethodTypeSignature sig = MethodTypeSignature.make("(Ljava/lang/String;)Ljava/lang/String;");
    assertThat(
        sig.getArguments(), arrayContaining(is(ClassTypeSignature.make("Ljava/lang/String;"))));
    assertThat(sig.getReturnType(), is(ClassTypeSignature.make("Ljava/lang/String;")));
  }

  @Test
  void intArrayTo2DStringArray() {
    MethodTypeSignature sig = MethodTypeSignature.make("([I)[[Ljava/lang/String;");
    assertThat(sig.getArguments(), arrayContaining(is(ArrayTypeSignature.make("[I"))));
    ArrayTypeSignature returnType = (ArrayTypeSignature) sig.getReturnType();
    assertThat(returnType, is(ArrayTypeSignature.make("[[Ljava/lang/String;")));
    assertThat(returnType.getContents(), is(ArrayTypeSignature.make("[Ljava/lang/String;")));
  }

  @Test
  void identityGeneric() {
    MethodTypeSignature sig = MethodTypeSignature.make("<T:Ljava/lang/Object;>(TT;)TT;");
    FormalTypeParameter[] formalTypeParameters = sig.getFormalTypeParameters();
    assertThat(formalTypeParameters.length, is(1));
    FormalTypeParameter formalTypeParameter = formalTypeParameters[0];
    assertThat(formalTypeParameter.getIdentifier(), is("T"));
    assertThat(formalTypeParameter.getClassBound(), is(ClassTypeSignature.make("Ljava/lang/Object;")));
    TypeSignature[] arguments = sig.getArguments();
    assertThat(arguments.length, is(1));
    TypeVariableSignature typeVariableSignature =
        (TypeVariableSignature) arguments[0];
    assertThat(typeVariableSignature.getIdentifier(), is("T"));
    TypeVariableSignature returnType = (TypeVariableSignature) sig.getReturnType();
    assertThat(returnType.getIdentifier(), is("T"));
  }

  @Test
  void arrayArgumentType() {
    assertThat(
        MethodTypeSignature.make("([I)V").getArguments(),
        arrayContaining(is(TypeSignature.make("[I"))));
    assertThat(
        MethodTypeSignature.make("([J[DB)V").getArguments(),
        arrayContaining(
            is(TypeSignature.make("[J")),
            is(TypeSignature.make("[D")),
            is(TypeSignature.make("B"))));
    assertThat(
        MethodTypeSignature.make("([Ljava/lang/String;B)V").getArguments(),
        arrayContaining(
            is(TypeSignature.make("[Ljava/lang/String;")), is(TypeSignature.make("B"))));
  }
}
