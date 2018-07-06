package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.term.concrete.Concrete;
import com.jetbrains.jetpad.vclang.term.concrete.ConcreteDefinitionVisitor;
import com.jetbrains.jetpad.vclang.term.concrete.ConcreteExpressionVisitor;

import java.util.*;

public class ConcreteCompareVisitor implements ConcreteExpressionVisitor<Concrete.Expression, Boolean>, ConcreteDefinitionVisitor<Concrete.Definition, Boolean> {
  private final Map<Referable, Referable> mySubstitution = new HashMap<>();

  public boolean compare(Concrete.Expression expr1, Concrete.Expression expr2) {
    if (expr1 instanceof Concrete.BinOpSequenceExpression && ((Concrete.BinOpSequenceExpression) expr1).getSequence().size() == 1) {
      expr1 = ((Concrete.BinOpSequenceExpression) expr1).getSequence().get(0).expression;
    }
    if (expr2 instanceof Concrete.BinOpSequenceExpression && ((Concrete.BinOpSequenceExpression) expr2).getSequence().size() == 1) {
      expr2 = ((Concrete.BinOpSequenceExpression) expr2).getSequence().get(0).expression;
    }
    return expr1.accept(this, expr2);
  }

  @Override
  public Boolean visitApp(Concrete.AppExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.AppExpression && compare(expr1.getFunction(), ((Concrete.AppExpression) expr2).getFunction()) && expr1.getArguments().size() == ((Concrete.AppExpression) expr2).getArguments().size())) {
      return false;
    }
    for (int i = 0; i < expr1.getArguments().size(); i++) {
      Concrete.Argument argument2 = ((Concrete.AppExpression) expr2).getArguments().get(i);
      if (!(expr1.getArguments().get(i).isExplicit() == argument2.isExplicit() && compare(expr1.getArguments().get(i).expression, argument2.expression))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitReference(Concrete.ReferenceExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.ReferenceExpression)) return false;
    Concrete.ReferenceExpression refExpr2 = (Concrete.ReferenceExpression) expr2;
    Referable ref1 = mySubstitution.get(expr1.getReferent());
    if (ref1 == null) {
      ref1 = expr1.getReferent();
    }
    return compareLevel(expr1.getPLevel(), refExpr2.getPLevel()) && compareLevel(expr1.getHLevel(), refExpr2.getHLevel()) && Objects.equals(expr1.getLowerIntBound(), refExpr2.getLowerIntBound()) && Objects.equals(expr1.getUpperIntBound(), refExpr2.getUpperIntBound()) && ref1.equals(refExpr2.getReferent());
  }

  @Override
  public Boolean visitInferenceReference(Concrete.InferenceReferenceExpression expr, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.InferenceReferenceExpression && expr.getVariable() == ((Concrete.InferenceReferenceExpression) expr2).getVariable();
  }

  private boolean compareParameter(Concrete.Parameter arg1, Concrete.Parameter arg2) {
    if (arg1.getExplicit() != arg2.getExplicit()) {
      return false;
    }
    if (arg1 instanceof Concrete.TelescopeParameter && arg2 instanceof Concrete.TelescopeParameter) {
      List<? extends Referable> list1 = ((Concrete.TelescopeParameter) arg1).getReferableList();
      List<? extends Referable> list2 = ((Concrete.TelescopeParameter) arg2).getReferableList();
      if (list1.size() != list2.size()) {
        return false;
      }
      for (int i = 0; i < list1.size(); i++) {
        mySubstitution.put(list1.get(i), list2.get(i));
      }
      return compare(((Concrete.TelescopeParameter) arg1).getType(), ((Concrete.TelescopeParameter) arg2).getType());
    }
    if (arg1 instanceof Concrete.TypeParameter && arg2 instanceof Concrete.TypeParameter) {
      return compare(((Concrete.TypeParameter) arg1).getType(), ((Concrete.TypeParameter) arg2).getType());
    }
    if (arg1 instanceof Concrete.NameParameter && arg2 instanceof Concrete.NameParameter) {
      mySubstitution.put(((Concrete.NameParameter) arg1).getReferable(), ((Concrete.NameParameter) arg2).getReferable());
      return true;
    }
    return false;
  }

  private boolean compareParameters(List<? extends Concrete.Parameter> args1, List<? extends Concrete.Parameter> args2) {
    if (args1.size() != args2.size()) return false;
    for (int i = 0; i < args1.size(); i++) {
      if (!compareParameter(args1.get(i), args2.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitLam(Concrete.LamExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.LamExpression && compareParameters(expr1.getParameters(), ((Concrete.LamExpression) expr2).getParameters()) && compare(expr1.getBody(), ((Concrete.LamExpression) expr2).getBody());
  }

  @Override
  public Boolean visitPi(Concrete.PiExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.PiExpression && compareParameters(expr1.getParameters(), ((Concrete.PiExpression) expr2).getParameters()) && compare(expr1.getCodomain(), ((Concrete.PiExpression) expr2).getCodomain());
  }

  @Override
  public Boolean visitUniverse(Concrete.UniverseExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.UniverseExpression)) {
      return false;
    }
    Concrete.UniverseExpression uni2 = (Concrete.UniverseExpression) expr2;
    return compareLevel(expr1.getPLevel(), uni2.getPLevel()) && compareLevel(expr1.getHLevel(), uni2.getHLevel());
  }

  public boolean compareLevel(Concrete.LevelExpression level1, Concrete.LevelExpression level2) {
    if (level1 == null) {
      return level2 == null || level2 instanceof Concrete.PLevelExpression || level2 instanceof Concrete.HLevelExpression;
    }
    if (level1 instanceof Concrete.PLevelExpression) {
      return level2 instanceof Concrete.PLevelExpression || level2 == null;
    }
    if (level1 instanceof Concrete.HLevelExpression) {
      return level2 instanceof Concrete.HLevelExpression || level2 == null;
    }
    if (level1 instanceof Concrete.InfLevelExpression) {
      return level2 instanceof Concrete.InfLevelExpression;
    }
    if (level1 instanceof Concrete.NumberLevelExpression) {
      return level2 instanceof Concrete.NumberLevelExpression && ((Concrete.NumberLevelExpression) level1).getNumber() == ((Concrete.NumberLevelExpression) level2).getNumber();
    }
    if (level1 instanceof Concrete.SucLevelExpression) {
      return level2 instanceof Concrete.SucLevelExpression && compareLevel(((Concrete.SucLevelExpression) level1).getExpression(), ((Concrete.SucLevelExpression) level2).getExpression());
    }
    if (level1 instanceof Concrete.MaxLevelExpression) {
      if (!(level2 instanceof Concrete.MaxLevelExpression)) {
        return false;
      }
      Concrete.MaxLevelExpression max1 = (Concrete.MaxLevelExpression) level1;
      Concrete.MaxLevelExpression max2 = (Concrete.MaxLevelExpression) level2;
      return compareLevel(max1.getLeft(), max2.getLeft()) && compareLevel(max1.getRight(), max2.getRight());
    }
    throw new IllegalStateException();
  }

  @Override
  public Boolean visitHole(Concrete.HoleExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.HoleExpression && (expr1.getError() == null) == (((Concrete.HoleExpression) expr2).getError() == null);
  }

  @Override
  public Boolean visitGoal(Concrete.GoalExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.GoalExpression;
  }

  @Override
  public Boolean visitTuple(Concrete.TupleExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.TupleExpression)) return false;
    return compareExpressionList(expr1.getFields(), ((Concrete.TupleExpression) expr2).getFields());
  }

  @Override
  public Boolean visitSigma(Concrete.SigmaExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.SigmaExpression && compareParameters(expr1.getParameters(), ((Concrete.SigmaExpression) expr2).getParameters());
  }

  @Override
  public Boolean visitBinOpSequence(Concrete.BinOpSequenceExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.BinOpSequenceExpression)) return false;
    Concrete.BinOpSequenceExpression binOpExpr2 = (Concrete.BinOpSequenceExpression) expr2;
    if (expr1.getSequence().size() != binOpExpr2.getSequence().size()) return false;
    for (int i = 0; i < expr1.getSequence().size(); i++) {
      if (expr1.getSequence().get(i).fixity != binOpExpr2.getSequence().get(i).fixity || expr1.getSequence().get(i).isExplicit != binOpExpr2.getSequence().get(i).isExplicit) return false;
      Concrete.Expression arg1 = expr1.getSequence().get(i).expression;
      Concrete.Expression arg2 = ((Concrete.BinOpSequenceExpression) expr2).getSequence().get(i).expression;
      if (!compare(arg1, arg2)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean comparePattern(Concrete.Pattern pattern1, Concrete.Pattern pattern2) {
    if (pattern1 instanceof Concrete.NamePattern) {
      if (!(pattern2 instanceof Concrete.NamePattern)) {
        return false;
      }
      mySubstitution.put(((Concrete.NamePattern) pattern1).getReferable(), ((Concrete.NamePattern) pattern2).getReferable());
      return true;
    }
    if (pattern1 instanceof Concrete.ConstructorPattern) {
      if (!(pattern2 instanceof Concrete.ConstructorPattern)) {
        return false;
      }

      Concrete.ConstructorPattern conPattern1 = (Concrete.ConstructorPattern) pattern1;
      Concrete.ConstructorPattern conPattern2 = (Concrete.ConstructorPattern) pattern2;
      if (!conPattern1.getConstructor().equals(conPattern2.getConstructor()) || conPattern1.getPatterns().size() != conPattern2.getPatterns().size()) {
        return false;
      }

      for (int i = 0; i < conPattern1.getPatterns().size(); i++) {
        if (!comparePattern(conPattern1.getPatterns().get(i), conPattern2.getPatterns().get(i))) {
          return false;
        }
      }

      return true;
    }
    return pattern1 instanceof Concrete.EmptyPattern && pattern2 instanceof Concrete.EmptyPattern;
  }

  private boolean compareClause(Concrete.Clause clause1, Concrete.Clause clause2) {
    if (clause1.getPatterns() == clause2.getPatterns()) {
      return true;
    }
    if (clause1.getPatterns() == null || clause2.getPatterns() == null) {
      return false;
    }
    if (clause1.getPatterns().size() != clause2.getPatterns().size()) {
      return false;
    }
    for (int i = 0; i < clause1.getPatterns().size(); i++) {
      if (!comparePattern(clause1.getPatterns().get(i), clause2.getPatterns().get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean compareFunctionClauses(List<Concrete.FunctionClause> clauses1, List<Concrete.FunctionClause> clauses2) {
    if (clauses1.size() != clauses2.size()) {
      return false;
    }
    for (int i = 0; i < clauses1.size(); i++) {
      if (!(compareClause(clauses1.get(i), clauses2.get(i)) && (clauses1.get(i).getExpression() == null ? clauses2.get(i).getExpression() == null : clauses2.get(i).getExpression() != null && compare(clauses1.get(i).getExpression(), clauses2.get(i).getExpression())))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitCase(Concrete.CaseExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.CaseExpression)) {
      return false;
    }
    Concrete.CaseExpression case2 = (Concrete.CaseExpression) expr2;
    return compareExpressionList(expr1.getExpressions(), case2.getExpressions()) && compareFunctionClauses(expr1.getClauses(), case2.getClauses());
  }

  @Override
  public Boolean visitProj(Concrete.ProjExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.ProjExpression && expr1.getField() == ((Concrete.ProjExpression) expr2).getField() && compare(expr1.getExpression(), ((Concrete.ProjExpression) expr2).getExpression());
  }

  private boolean compareImplementStatements(List<Concrete.ClassFieldImpl> implStats1, List<Concrete.ClassFieldImpl> implStats2) {
    if (implStats1.size() != implStats2.size()) {
      return false;
    }
    for (int i = 0; i < implStats1.size(); i++) {
      if (!(compare(implStats1.get(i).getImplementation(), implStats2.get(i).getImplementation()) && Objects.equals(implStats1.get(i).getImplementedField(), implStats2.get(i).getImplementedField()))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitClassExt(Concrete.ClassExtExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.ClassExtExpression)) return false;
    Concrete.ClassExtExpression classExtExpr2 = (Concrete.ClassExtExpression) expr2;
    return compare(expr1.getBaseClassExpression(), classExtExpr2.getBaseClassExpression()) && compareImplementStatements(expr1.getStatements(), classExtExpr2.getStatements());
  }

  @Override
  public Boolean visitNew(Concrete.NewExpression expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.NewExpression && compare(expr1.getExpression(), ((Concrete.NewExpression) expr2).getExpression());
  }

  private boolean compareLetClause(Concrete.LetClause clause1, Concrete.LetClause clause2) {
    return compareParameters(clause1.getParameters(), clause2.getParameters()) && compare(clause1.getTerm(), clause2.getTerm()) && (clause1.getResultType() == null && clause2.getResultType() == null || clause1.getResultType() != null && clause2.getResultType() != null && compare(clause1.getResultType(), clause2.getResultType()));
  }

  @Override
  public Boolean visitLet(Concrete.LetExpression expr1, Concrete.Expression expr2) {
    if (!(expr2 instanceof Concrete.LetExpression)) return false;
    Concrete.LetExpression letExpr2 = (Concrete.LetExpression) expr2;
    if (expr1.getClauses().size() != letExpr2.getClauses().size()) {
      return false;
    }
    for (int i = 0; i < expr1.getClauses().size(); i++) {
      if (!compareLetClause(expr1.getClauses().get(i), letExpr2.getClauses().get(i))) {
        return false;
      }
      mySubstitution.put(expr1.getClauses().get(i).getData(), letExpr2.getClauses().get(i).getData());
    }
    return compare(expr1.getExpression(), letExpr2.getExpression());
  }

  @Override
  public Boolean visitNumericLiteral(Concrete.NumericLiteral expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.NumericLiteral && expr1.getNumber().equals(((Concrete.NumericLiteral) expr2).getNumber());
  }

  private boolean compareExpressionList(List<? extends Concrete.Expression> list1, List<? extends Concrete.Expression> list2) {
    if (list1.size() != list2.size()) {
      return false;
    }
    for (int i = 0; i < list1.size(); i++) {
      if (!compare(list1.get(i), list2.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean compare(Concrete.ReferableDefinition def1, Concrete.ReferableDefinition def2) {
    ConcreteCompareVisitor visitor = new ConcreteCompareVisitor();
    if (def1 instanceof Concrete.Definition) {
      visitor.mySubstitution.put(def1.getData(), def2.getData());
      return def2 instanceof Concrete.Definition && Objects.equals(((Concrete.Definition) def1).enclosingClass, ((Concrete.Definition) def2).enclosingClass) && ((Concrete.Definition) def1).accept(visitor, (Concrete.Definition) def2);
    }
    if (def1 instanceof Concrete.Constructor) {
      return def2 instanceof Concrete.Constructor && visitor.compareConstructor((Concrete.Constructor) def1, (Concrete.Constructor) def2);
    }
    if (def1 instanceof Concrete.ClassField) {
      return def2 instanceof Concrete.ClassField && visitor.compareField((Concrete.ClassField) def1, (Concrete.ClassField) def2);
    }
    return false;
  }

  @Override
  public Boolean visitFunction(Concrete.FunctionDefinition def, Concrete.Definition def2) {
    if (!(def2 instanceof Concrete.FunctionDefinition)) {
      return false;
    }
    Concrete.FunctionDefinition fun2 = (Concrete.FunctionDefinition) def2;

    if (!compareParameters(def.getParameters(), fun2.getParameters())) {
      return false;
    }
    if ((def.getResultType() != null || fun2.getResultType() != null) && (def.getResultType() == null || fun2.getResultType() == null || !compare(def.getResultType(), fun2.getResultType()))) {
      return false;
    }
    if (def.getBody() instanceof Concrete.TermFunctionBody) {
      return fun2.getBody() instanceof Concrete.TermFunctionBody && compare(((Concrete.TermFunctionBody) def.getBody()).getTerm(), ((Concrete.TermFunctionBody) fun2.getBody()).getTerm());
    } else
    if (def.getBody() instanceof Concrete.ElimFunctionBody) {
      if (!(fun2.getBody() instanceof Concrete.ElimFunctionBody)) {
        return false;
      }
      Concrete.ElimFunctionBody elim1 = (Concrete.ElimFunctionBody) def.getBody();
      Concrete.ElimFunctionBody elim2 = (Concrete.ElimFunctionBody) fun2.getBody();
      return compareExpressionList(elim1.getEliminatedReferences(), elim2.getEliminatedReferences()) && compareFunctionClauses(elim1.getClauses(), elim2.getClauses());
    } else {
      return false;
    }
  }

  @Override
  public Boolean visitData(Concrete.DataDefinition def, Concrete.Definition def2) {
    if (!(def2 instanceof Concrete.DataDefinition)) {
      return false;
    }
    Concrete.DataDefinition data2 = (Concrete.DataDefinition) def2;

    if (!compareParameters(def.getParameters(), data2.getParameters())) {
      return false;
    }
    List<Concrete.ReferenceExpression> elimRefs1 = def.getEliminatedReferences();
    List<Concrete.ReferenceExpression> elimRefs2 = data2.getEliminatedReferences();
    if (elimRefs1 == null && elimRefs2 != null || elimRefs1 != null && elimRefs2 == null) {
      return false;
    }
    if (elimRefs1 != null) {
      if (elimRefs1.size() != elimRefs2.size()) {
        return false;
      }
      for (int i = 0; i < elimRefs1.size(); i++) {
        if (!compare(elimRefs1.get(i), elimRefs2.get(i))) {
          return false;
        }
      }
    }
    if (def.isTruncated() != data2.isTruncated()) {
      return false;
    }
    if (def.getConstructorClauses().size() != data2.getConstructorClauses().size()) {
      return false;
    }
    for (int i = 0; i < def.getConstructorClauses().size(); i++) {
      if (!compareClause(def.getConstructorClauses().get(i), data2.getConstructorClauses().get(i))) {
        return false;
      }
      if (def.getConstructorClauses().get(i).getConstructors().size() != data2.getConstructorClauses().get(i).getConstructors().size()) {
        return false;
      }
      for (int j = 0; j < def.getConstructorClauses().get(i).getConstructors().size(); j++) {
        if (!compareConstructor(def.getConstructorClauses().get(i).getConstructors().get(j), data2.getConstructorClauses().get(i).getConstructors().get(j))) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean compareConstructor(Concrete.Constructor con1, Concrete.Constructor con2) {
    mySubstitution.put(con1.getData(), con2.getData());
    return compareParameters(con1.getParameters(), con2.getParameters()) && compareExpressionList(con1.getEliminatedReferences(), con2.getEliminatedReferences()) && compareFunctionClauses(con1.getClauses(), con2.getClauses());
  }

  @Override
  public Boolean visitClass(Concrete.ClassDefinition def, Concrete.Definition def2) {
    if (!(def2 instanceof Concrete.ClassDefinition)) {
      return false;
    }
    Concrete.ClassDefinition class2 = (Concrete.ClassDefinition) def2;

    if (!compareExpressionList(def.getSuperClasses(), class2.getSuperClasses())) {
      return false;
    }
    if (def.getFields().size() != class2.getFields().size()) {
      return false;
    }
    for (int i = 0; i < def.getFields().size(); i++) {
      if (!compareField(def.getFields().get(i), class2.getFields().get(i))) {
        return false;
      }
    }
    return def.getFieldsExplicitness().equals(class2.getFieldsExplicitness()) && compareImplementStatements(def.getImplementations(), class2.getImplementations()) && Objects.equals(def.getCoercingField(), class2.getCoercingField());
  }

  private boolean compareField(Concrete.ClassField field1, Concrete.ClassField field2) {
    mySubstitution.put(field1.getData(), field2.getData());
    return field1.isExplicit() == field2.isExplicit() && compare(field1.getResultType(), field2.getResultType());
  }

  @Override
  public Boolean visitInstance(Concrete.Instance def, Concrete.Definition def2) {
    if (!(def2 instanceof Concrete.Instance)) {
      return false;
    }
    Concrete.Instance inst2 = (Concrete.Instance) def2;
    return compareParameters(def.getParameters(), inst2.getParameters()) && compare(def.getResultType(), inst2.getResultType()) && compareImplementStatements(def.getClassFieldImpls(), inst2.getClassFieldImpls());
  }
}
