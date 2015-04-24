package com.jetbrains.jetpad.vclang.parser;

import com.jetbrains.jetpad.vclang.term.definition.Definition;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.Abstract;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.arg.TelescopeArgument;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.parser.ParserTestCase.parseDef;
import static com.jetbrains.jetpad.vclang.parser.ParserTestCase.parseExpr;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static org.junit.Assert.assertEquals;

public class PrettyPrintingParserTest {
  private void testExpr(Expression expected, Expression expr) throws UnsupportedEncodingException {
    StringBuilder builder = new StringBuilder();
    expr.prettyPrint(builder, new ArrayList<String>(), Abstract.Expression.PREC);
    Expression result = parseExpr(builder.toString());
    assertEquals(expected, result);
  }

  private void testDef(FunctionDefinition expected, FunctionDefinition def) throws UnsupportedEncodingException {
    StringBuilder builder = new StringBuilder();
    def.prettyPrint(builder, new ArrayList<String>(), Abstract.Expression.PREC);
    FunctionDefinition result = (FunctionDefinition) parseDef(builder.toString());
    assertEquals(expected.getType(), result.getType());
    assertEquals(expected.getArrow(), result.getArrow());
    assertEquals(expected.getTerm(), result.getTerm());
  }

  @Test
  public void prettyPrintingParserLamApp() throws UnsupportedEncodingException {
    // (\x y. x (x y)) (\x y. x) ((\x. x) (\x. x))
    Expression expected = Apps(Lam(lamArgs(Name("x"), Name("y")), Apps(Var("x"), Apps(Var("x"), Var("y")))), Lam(lamArgs(Name("x"), Name("y")), Var("x")), Apps(Lam("x", Var("x")), Lam("x", Var("x"))));
    Expression expr = Apps(Lam(lamArgs(Name("x"), Name("y")), Apps(Index(1), Apps(Index(1), Index(0)))), Lam(lamArgs(Name("x"), Name("y")), Index(1)), Apps(Lam("x", Index(0)), Lam("x", Index(0))));
    testExpr(expected, expr);
  }

  @Test
  public void prettyPrintingParserPi() throws UnsupportedEncodingException {
    // (x y : N) -> N -> N -> (x y -> y x) -> N x y
    Expression expected = Pi(args(Tele(vars("x", "y"), Nat())), Pi(Nat(), Pi(Nat(), Pi(Pi(Apps(Var("x"), Var("y")), Apps(Var("y"), Var("x"))), Apps(Nat(), Var("x"), Var("y"))))));
    Expression expr = Pi(args(Tele(vars("x", "y"), Nat())), Pi(Nat(), Pi(Nat(), Pi(Pi(Apps(Index(1), Index(0)), Apps(Index(0), Index(1))), Apps(Nat(), Index(1), Index(0))))));
    testExpr(expected, expr);
  }

  @Test
  public void prettyPrintingParserPiImplicit() throws UnsupportedEncodingException {
    // (x : N) {y z : N} -> N -> (t z' : N) {x' : N -> N} -> N x' y z' t
    Expression expected = Pi("x", Nat(), Pi(args(Tele(false, vars("y", "z"), Nat())), Pi(Nat(), Pi(args(Tele(vars("t", "z'"), Nat())), Pi(false, "x'", Pi(Nat(), Nat()), Apps(Nat(), Var("x'"), Var("y"), Var("z'"), Var("t")))))));
    Expression expr = Pi("x", Nat(), Pi(args(Tele(false, vars("y", "z"), Nat())), Pi(Nat(), Pi(args(Tele(vars("t", "z'"), Nat())), Pi(false, "x'", Pi(Nat(), Nat()), Apps(Nat(), Index(0), Index(4), Index(1), Index(2)))))));
    testExpr(expected, expr);
  }

  @Test
  public void prettyPrintingParserFunDef() throws UnsupportedEncodingException {
    // f (x : N) : N x => \y z. y z;
    List<TelescopeArgument> arguments = new ArrayList<>();
    arguments.add(Tele(vars("x"), Nat()));
    FunctionDefinition expected = new FunctionDefinition("f", arguments, Apps(Nat(), Var("x")), Definition.Arrow.RIGHT, Lam(lamArgs(Name("y"), Name("z")), Apps(Var("y"), Var("z"))));
    FunctionDefinition def = new FunctionDefinition("f", arguments, Apps(Nat(), Index(0)), Definition.Arrow.RIGHT, Lam(lamArgs(Name("y"), Name("z")), Apps(Index(1), Index(0))));
    testDef(expected, def);
  }
}
