package com.jetbrains.jetpad.vclang.parser;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.module.source.ModuleSourceId;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Concrete;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.jetpad.vclang.parser.VcgrammarParser.*;

public class BuildVisitor extends VcgrammarBaseVisitor {
  private final ModuleSourceId myModule;
  private final ErrorReporter myErrorReporter;

  public BuildVisitor(ModuleSourceId module, ErrorReporter errorReporter) {
    myModule = module;
    myErrorReporter = errorReporter;
  }

  private Concrete.NameArgument getVar(AtomFieldsAccContext ctx) {
    if (!ctx.fieldAcc().isEmpty() || !(ctx.atom() instanceof AtomLiteralContext)) {
      return null;
    }
    LiteralContext literal = ((AtomLiteralContext) ctx.atom()).literal();
    if (literal instanceof UnknownContext) {
      return new Concrete.NameArgument(tokenPosition(literal.getStart()), true, "_");
    }
    if (literal instanceof IdContext && ((IdContext) literal).name() instanceof NameIdContext) {
      return new Concrete.NameArgument(tokenPosition(literal.getStart()), true, ((NameIdContext) ((IdContext) literal).name()).ID().getText());
    }
    return null;
  }

  private List<Concrete.NameArgument> getVarsNull(ExprContext expr) {
    if (!(expr instanceof BinOpContext && ((BinOpContext) expr).binOpLeft().isEmpty())) {
      return null;
    }
    Concrete.NameArgument firstArg = getVar(((BinOpContext) expr).atomFieldsAcc());
    if (firstArg == null) {
      return null;
    }

    List<Concrete.NameArgument> result = new ArrayList<>();
    result.add(firstArg);
    for (ArgumentContext argument : ((BinOpContext) expr).argument()) {
      if (argument instanceof ArgumentExplicitContext) {
        Concrete.NameArgument arg = getVar(((ArgumentExplicitContext) argument).atomFieldsAcc());
        if (arg == null) {
          return null;
        }
        result.add(arg);
      } else {
        List<Concrete.NameArgument> arguments = getVarsNull(((ArgumentImplicitContext) argument).expr());
        if (arguments == null) {
          return null;
        }
        for (Concrete.NameArgument arg : arguments) {
          arg.setExplicit(false);
          result.add(arg);
        }
      }
    }
    return result;
  }

  private List<Concrete.NameArgument> getVars(ExprContext expr) {
    List<Concrete.NameArgument> result = getVarsNull(expr);
    if (result == null) {
      myErrorReporter.report(new ParserError(tokenPosition(expr.getStart()), "Expected a list of variables"));
      return null;
    } else {
      return result;
    }
  }

  public Concrete.Expression visitExpr(ExprContext expr) {
    if (expr == null) return null;
    return (Concrete.Expression) visit(expr);
  }

  public Concrete.Expression visitExpr(AtomContext expr) {
    return (Concrete.Expression) visit(expr);
  }

  @Override
  public List<String> visitModulePath(ModulePathContext ctx) {
    List<String> path = new ArrayList<>(ctx.ID().size());
    for (TerminalNode id : ctx.ID()) {
      path.add(id.getText());
    }
    return path;
  }

  @Override
  public Concrete.ModuleCallExpression visitAtomModuleCall(AtomModuleCallContext ctx) {
    return new Concrete.ModuleCallExpression(tokenPosition(ctx.getStart()), visitModulePath(ctx.modulePath()));
  }

  public Concrete.Expression visitExpr(LiteralContext expr) {
    return (Concrete.Expression) visit(expr);
  }

  private List<Concrete.Statement> visitStatementList(List<StatementContext> statementCtxs) {
    List<Concrete.Statement> statements = new ArrayList<>(statementCtxs.size());
    for (StatementContext statementCtx : statementCtxs) {
      Concrete.Statement statement = (Concrete.Statement) visit(statementCtx);
      if (statement != null) {
        statements.add(statement);
      }
    }
    return statements;
  }

  @Override
  public List<Concrete.Statement> visitStatements(StatementsContext ctx) {
    if (ctx == null) return null;
    return visitStatementList(ctx.statement());
  }

  public Concrete.Definition visitDefinition(DefinitionContext ctx) {
    return (Concrete.Definition) visit(ctx);
  }

  @Override
  public Concrete.DefineStatement visitStatDef(StatDefContext ctx) {
    if (ctx == null) return null;
    Concrete.Definition definition = visitDefinition(ctx.definition());
    if (definition == null) {
      return null;
    }
    return new Concrete.DefineStatement(definition.getPosition(), definition);
  }

  @Override
  public Concrete.NamespaceCommandStatement visitStatCmd(StatCmdContext ctx) {
    if (ctx == null) return null;
    Abstract.NamespaceCommandStatement.Kind kind = (Abstract.NamespaceCommandStatement.Kind) visit(ctx.nsCmd());

    List<String> modulePath = ctx.nsCmdRoot().modulePath() == null ? null : visitModulePath(ctx.nsCmdRoot().modulePath());
    List<String> path = new ArrayList<>();
    if (ctx.nsCmdRoot().name() != null) {
      String name = visitName(ctx.nsCmdRoot().name());
      if (name == null) return null;
      path.add(name);
    }
    for (FieldAccContext fieldAccContext : ctx.fieldAcc()) {
      if (fieldAccContext instanceof ClassFieldContext) {
        String name = visitName(((ClassFieldContext) fieldAccContext).name());
        if (name == null) return null;
        path.add(name);
      } else {
        myErrorReporter.report(new ParserError(tokenPosition(fieldAccContext.getStart()), "Expected a name"));
      }
    }

    List<String> names;
    if (!ctx.name().isEmpty()) {
      names = new ArrayList<>(ctx.name().size());
      for (NameContext nameCtx : ctx.name()) {
        String name = visitName(nameCtx);
        if (name == null) return null;
        names.add(name);
      }
    } else {
      names = null;
    }
    return new Concrete.NamespaceCommandStatement(tokenPosition(ctx.getStart()), kind, modulePath, path, ctx.hidingOpt() instanceof WithHidingContext, names);
  }

  @Override
  public Abstract.NamespaceCommandStatement.Kind visitOpenCmd(OpenCmdContext ctx) {
    return Abstract.NamespaceCommandStatement.Kind.OPEN;
  }

  @Override
  public Abstract.NamespaceCommandStatement.Kind visitExportCmd(ExportCmdContext ctx) {
    return Abstract.NamespaceCommandStatement.Kind.EXPORT;
  }

  public Abstract.Definition.Arrow visitArrow(ArrowContext arrowCtx) {
    return arrowCtx instanceof ArrowLeftContext ? Abstract.Definition.Arrow.LEFT : arrowCtx instanceof ArrowRightContext ? Abstract.Definition.Arrow.RIGHT : null;
  }

  public Precedence visitPrecedence(PrecedenceContext ctx) {
    return ctx == null ? null : (Precedence) visit(ctx);
  }

  @Override
  public Precedence visitNoPrecedence(NoPrecedenceContext ctx) {
    return Precedence.DEFAULT;
  }

  @Override
  public Precedence visitWithPrecedence(WithPrecedenceContext ctx) {
    if (ctx == null) return null;
    int priority = Integer.valueOf(ctx.NUMBER().getText());
    if (priority < 1 || priority > 9) {
      myErrorReporter.report(new ParserError(tokenPosition(ctx.NUMBER().getSymbol()), "Precedence out of range: " + priority));

      if (priority < 1) {
        priority = 1;
      } else {
        priority = 9;
      }
    }

    return new Precedence((Precedence.Associativity) visit(ctx.associativity()), (byte) priority);
  }

  @Override
  public Precedence.Associativity visitNonAssoc(NonAssocContext ctx) {
    return Precedence.Associativity.NON_ASSOC;
  }

  @Override
  public Precedence.Associativity visitLeftAssoc(LeftAssocContext ctx) {
    return Precedence.Associativity.LEFT_ASSOC;
  }

  @Override
  public Precedence.Associativity visitRightAssoc(RightAssocContext ctx) {
    return Precedence.Associativity.RIGHT_ASSOC;
  }

  @Override
  public Concrete.NamePattern visitAnyPatternAny(AnyPatternAnyContext ctx) {
    return new Concrete.NamePattern(tokenPosition(ctx.start), null);
  }

  @Override
  public Concrete.AnyConstructorPattern visitAnyPatternConstructor(AnyPatternConstructorContext ctx) {
    return new Concrete.AnyConstructorPattern(tokenPosition(ctx.start));
  }

  @Override
  public Concrete.Pattern visitPatternAny(PatternAnyContext ctx) {
    return (Concrete.Pattern) visit(ctx.anyPattern());
  }

  @Override
  public Concrete.Pattern visitPatternConstructor(PatternConstructorContext ctx) {
    if (ctx.name() instanceof NameIdContext && ctx.patternArg().size() == 0) {
      return new Concrete.NamePattern(tokenPosition(ctx.start), ((NameIdContext) ctx.name()).ID().getText());
    } else {
      String name = visitName(ctx.name());
      if (name == null) return null;
      return new Concrete.ConstructorPattern(tokenPosition(ctx.start), name, visitPatternArgs(ctx.patternArg()));
    }
  }

  @Override
  public Concrete.PatternArgument visitPatternArgExplicit(PatternArgExplicitContext ctx) {
    return new Concrete.PatternArgument(tokenPosition(ctx.start), (Concrete.Pattern) visit(ctx.pattern()), true, false);
  }

  @Override
  public Concrete.PatternArgument visitPatternArgImplicit(PatternArgImplicitContext ctx) {
    return new Concrete.PatternArgument(tokenPosition(ctx.start), (Concrete.Pattern) visit(ctx.pattern()), false, false);
  }

  @Override
  public Concrete.PatternArgument visitPatternArgAny(PatternArgAnyContext ctx) {
    return new Concrete.PatternArgument(tokenPosition(ctx.start), (Concrete.Pattern) visit(ctx.anyPattern()), true, false);
  }

  @Override
  public Concrete.PatternArgument visitPatternArgID(PatternArgIDContext ctx) {
    return new Concrete.PatternArgument(tokenPosition(ctx.start), new Concrete.NamePattern(tokenPosition(ctx.start), ctx.ID().getText()), true, false);
  }

  private List<Concrete.PatternArgument> visitPatternArgs(List<PatternArgContext> patternArgContexts) {
    List<Concrete.PatternArgument> patterns = new ArrayList<>();
    for (PatternArgContext patternArgCtx : patternArgContexts) {
      patterns.add((Concrete.PatternArgument) visit(patternArgCtx));
    }
    return patterns;
  }

  @Override
  public Concrete.ClassField visitDefAbstract(DefAbstractContext ctx) {
    if (ctx == null) return null;
    String name = visitName(ctx.name());
    Precedence precedence = visitPrecedence(ctx.precedence());
    Concrete.Expression resultType = visitExpr(ctx.expr());
    if (name == null || precedence == null || resultType == null) {
      return null;
    }

    return new Concrete.ClassField(tokenPosition(ctx.getStart()), name, precedence, Collections.<Concrete.Argument>emptyList(), resultType);
  }

  @Override
  public Concrete.ClassView visitDefClassView(DefClassViewContext ctx) {
    if (ctx == null) return null;
    List<Concrete.ClassViewField> fields = new ArrayList<>(ctx.classViewField().size());

    Concrete.Expression expr = null;
    if (ctx.expr() != null) {
      expr = visitExpr(ctx.expr());
      if (expr == null) {
        return null;
      }
    }

    String classifyingField = visitName(ctx.name());
    if (classifyingField == null) {
      return null;
    }
    Concrete.Position pos = tokenPosition(ctx.expr() != null ? ctx.expr().getStart() : ctx.ID(ctx.ID().size() > 1 ? 1 : 0).getSymbol());
    Concrete.ClassView classView = new Concrete.ClassView(tokenPosition(ctx.getStart()), ctx.ID(0).getText(), new Concrete.DefCallExpression(pos, expr, ctx.ID(ctx.ID().size() > 1 ? 1 : 0).getText()), classifyingField, fields);

    for (ClassViewFieldContext classViewFieldContext : ctx.classViewField()) {
      Concrete.ClassViewField field = visitClassViewField(classViewFieldContext, classView);
      if (field != null) {
        fields.add(field);
      }
    }

    return classView;
  }

  public Concrete.ClassViewField visitClassViewField(ClassViewFieldContext ctx, Concrete.ClassView classView) {
    if (ctx == null) return null;
    String underlyingField = visitName(ctx.name(0));
    String name = ctx.name().size() > 1 ? visitName(ctx.name().get(1)) : underlyingField;
    Precedence precedence = visitPrecedence(ctx.precedence());
    if (underlyingField == null || ctx.name().size() > 1 && name == null) {
      return null;
    }
    return new Concrete.ClassViewField(tokenPosition(ctx.name(0).getStart()), name, precedence != null ? precedence : Precedence.DEFAULT, underlyingField, classView);
  }

  @Override
  public Concrete.ClassViewField visitClassViewField(ClassViewFieldContext ctx) {
    throw new IllegalStateException();
  }

  @Override
  public Concrete.ClassViewInstance visitDefInstance(DefInstanceContext ctx) {
    if (ctx == null) return null;
    List<Concrete.Argument> arguments = visitFunctionArguments(ctx.tele());
    Concrete.Expression term = visitExpr(ctx.expr());
    if (term == null) {
      return null;
    }
    return new Concrete.ClassViewInstance(tokenPosition(ctx.getStart()), ctx.defaultInst() instanceof WithDefaultContext, ctx.ID().getText(), Precedence.DEFAULT, arguments, term);
  }

  @Override
  public List<Concrete.Statement> visitWhere(WhereContext ctx) {
    if (ctx == null) {
      return Collections.emptyList();
    }
    if (ctx.statements() != null && ctx.statements().statement() != null) {
      return visitStatementList(ctx.statements().statement());
    }
    if (ctx.statement() != null) {
      return visitStatementList(Collections.singletonList(ctx.statement()));
    }
    return null;
  }

  @Override
  public Concrete.FunctionDefinition visitDefFunction(DefFunctionContext ctx) {
    if (ctx == null) return null;
    String name = visitName(ctx.name());
    Precedence precedence = visitPrecedence(ctx.precedence());
    Concrete.Expression resultType = ctx.expr().size() == 2 ? visitExpr(ctx.expr(0)) : null;
    Abstract.Definition.Arrow arrow = visitArrow(ctx.arrow());
    Concrete.Expression term = visitExpr(ctx.expr().size() == 2 ? ctx.expr(1) : ctx.expr(0));
    List<Concrete.Argument> arguments = visitFunctionArguments(ctx.tele());
    List<Concrete.Statement> statements = visitWhere(ctx.where());
    if (name == null || precedence == null || ctx.expr().size() == 2 && resultType == null || arrow == null || term == null || statements == null) {
      return null;
    }

    return new Concrete.FunctionDefinition(tokenPosition(ctx.getStart()), name, precedence, arguments, resultType, arrow, term, statements);
  }

  private List<Concrete.Argument> visitFunctionArguments(List<TeleContext> teleCtx) {
    List<Concrete.Argument> arguments = new ArrayList<>();
    for (TeleContext tele : teleCtx) {
      List<Concrete.Argument> args = visitLamTele(tele);
      if (args != null) {
        if (args.get(0) instanceof Concrete.TelescopeArgument) {
          arguments.add(args.get(0));
        } else {
          myErrorReporter.report(new ParserError(tokenPosition(tele.getStart()), "Expected a typed variable"));
        }
      }
    }
    return arguments;
  }

  @Override
  public Concrete.DataDefinition visitDefData(DefDataContext ctx) {
    if (ctx == null) return null;
    String name = visitName(ctx.name());
    List<Concrete.TypeArgument> parameters = visitTeles(ctx.tele());
    Precedence precedence = visitPrecedence(ctx.precedence());
    if (name == null || parameters == null || precedence == null) {
      return null;
    }

    /* Abstract.UniverseExpression.Universe universe = null;
    if (ctx.expr() != null) {
      Concrete.Expression expression = (Concrete.Expression) visit(ctx.expr());
      if (expression instanceof Concrete.UniverseExpression) {
        universe = ((Concrete.UniverseExpression) expression).getUniverse();
      } else {
        myErrorReporter.report(new ParserError(expression.getPosition(), "Expected a universe"));
      }
    } /**/

    Concrete.Expression universe = ctx.expr() == null ? null : (Concrete.Expression) visit(ctx.expr());

    List<Concrete.Constructor> constructors = new ArrayList<>(ctx.constructorDef().size());
    List<Concrete.Condition> conditions = ctx.conditionDef() != null ? visitConditionDef(ctx.conditionDef()) : null;
    Concrete.DataDefinition dataDefinition = new Concrete.DataDefinition(tokenPosition(ctx.getStart()), name, precedence, parameters, universe, constructors, conditions);
    for (ConstructorDefContext constructorDefContext : ctx.constructorDef()) {
      visitConstructorDef(constructorDefContext, dataDefinition);
    }
    return dataDefinition;
  }

  @Override
  public List<Concrete.Condition> visitConditionDef(ConditionDefContext ctx) {
    List<Concrete.Condition> result = new ArrayList<>(ctx.condition().size());
    for (ConditionContext conditionCtx : ctx.condition()) {
      Concrete.Condition condition = visitCondition(conditionCtx);
      if (condition == null)
        return null;
      result.add(visitCondition(conditionCtx));
    }
    return result;
  }

  public Concrete.Condition visitCondition(ConditionContext ctx) {
    Concrete.Expression term = visitExpr(ctx.expr());
    String name = visitName(ctx.name());
    return term != null && name != null ? new Concrete.Condition(tokenPosition(ctx.start), name, visitPatternArgs(ctx.patternArg()), term) : null;
  }

  private void visitConstructorDef(ConstructorDefContext ctx, Concrete.DataDefinition def) {
    List<Concrete.PatternArgument> patterns = null;

    if (ctx instanceof WithPatternsContext) {
      WithPatternsContext wpCtx = (WithPatternsContext) ctx;
      String dataDefName = visitName(wpCtx.name());
      if (dataDefName == null) return;
      if (!def.getName().equals(dataDefName)) {
        myErrorReporter.report(new ParserError(tokenPosition(wpCtx.name().getStart()), "Expected a data type name: " + def.getName()));
        return;
      }

      patterns = visitPatternArgs(wpCtx.patternArg());
    }

    List<ConstructorContext> constructorCtxs = ctx instanceof WithPatternsContext ?
        ((WithPatternsContext) ctx).constructor() : Collections.singletonList(((NoPatternsContext) ctx).constructor());

    for (ConstructorContext conCtx : constructorCtxs) {
      String conName = visitName(conCtx.name());
      List<Concrete.TypeArgument> arguments = visitTeles(conCtx.tele());
      if (conName == null || arguments == null) {
        continue;
      }
      def.getConstructors().add(new Concrete.Constructor(tokenPosition(conCtx.name().getStart()), conName, visitPrecedence(conCtx.precedence()), arguments, def, patterns));
    }
  }

  private void misplacedDefinitionError(Concrete.Position position) {
    myErrorReporter.report(new ParserError(myModule, position, "This definition is not allowed here"));
  }

  private List<Concrete.Definition> visitInstanceStatements(List<StatementContext> ctx, List<Concrete.ClassField> fields, List<Concrete.Implementation> implementations) {
    List<Concrete.Definition> definitions = new ArrayList<>(ctx.size());
    for (StatementContext statementCtx : ctx) {
      Concrete.SourceNode sourceNode = (Concrete.SourceNode) visit(statementCtx);
      if (sourceNode != null) {
        if (sourceNode instanceof Concrete.DefineStatement) {
          Concrete.Definition definition = ((Concrete.DefineStatement) sourceNode).getDefinition();
          if (definition instanceof Concrete.ClassField) {
            if (fields != null) {
              fields.add((Concrete.ClassField) definition);
            } else {
              misplacedDefinitionError(definition.getPosition());
            }
          } else
          if (definition instanceof Concrete.Implementation) {
            if (implementations != null) {
              implementations.add((Concrete.Implementation) definition);
            } else {
              misplacedDefinitionError(definition.getPosition());
            }
          } else
          // TODO: Allow only function and data.
          if (definition instanceof Concrete.FunctionDefinition || definition instanceof Concrete.DataDefinition || definition instanceof Concrete.ClassDefinition) {
            definitions.add(definition);
          } else {
            misplacedDefinitionError(definition.getPosition());
          }
        } else {
          misplacedDefinitionError(sourceNode.getPosition());
        }
      }
    }
    return definitions;
  }

  @Override
  public Concrete.ClassDefinition visitDefClass(DefClassContext ctx) {
    if (ctx == null) return null;

    List<Concrete.SuperClass> superClasses = new ArrayList<>(ctx.atomFieldsAcc().size());
    List<Concrete.ClassField> fields = new ArrayList<>();
    List<Concrete.Implementation> implementations = new ArrayList<>();
    List<Concrete.Statement> globalStatements = visitWhere(ctx.where());
    List<Concrete.Definition> instanceDefinitions =
        ctx.statements() == null || ctx.statements().statement() == null ?
        Collections.<Concrete.Definition>emptyList() :
        visitInstanceStatements(ctx.statements().statement(), fields, implementations);
    for (AtomFieldsAccContext atomFieldsCtx : ctx.atomFieldsAcc()) {
      Concrete.Expression superExpr = visitAtomFieldsAcc(atomFieldsCtx);
      if (superExpr != null) {
        superClasses.add(new Concrete.SuperClass(tokenPosition(atomFieldsCtx.getStart()), superExpr));
      }
    }

    Concrete.ClassDefinition classDefinition = new Concrete.ClassDefinition(tokenPosition(ctx.getStart()), ctx.ID().getText(), superClasses, fields, implementations, globalStatements, instanceDefinitions);
    for (Concrete.ClassField field : fields) {
      field.setParent(classDefinition);
    }
    for (Concrete.Implementation implementation : implementations) {
      implementation.setParent(classDefinition);
    }
    for (Concrete.Definition definition : instanceDefinitions) {
      definition.setParent(classDefinition);
      definition.setIsStatic(false);
    }
    for (Concrete.Statement statement : globalStatements) {
      if (statement instanceof Concrete.DefineStatement) {
        ((Concrete.DefineStatement) statement).getDefinition().setParent(classDefinition);
      }
    }
    return classDefinition;
  }

  @Override
  public Concrete.Implementation visitDefImplement(DefImplementContext ctx) {
    if (ctx == null) return null;
    Concrete.Expression expression = visitExpr(ctx.expr());
    String name = visitName(ctx.name());
    return expression == null || name == null ? null : new Concrete.Implementation(tokenPosition(ctx.getStart()), name, expression);
  }

  @Override
  public Concrete.InferHoleExpression visitUnknown(UnknownContext ctx) {
    if (ctx == null) return null;
    return new Concrete.InferHoleExpression(tokenPosition(ctx.getStart()));
  }

  @Override
  public Concrete.ErrorExpression visitHole(HoleContext ctx) {
    if (ctx == null) return null;
    return new Concrete.ErrorExpression(tokenPosition(ctx.getStart()));
  }

  @Override
  public Concrete.PiExpression visitArr(ArrContext ctx) {
    if (ctx == null) return null;
    Concrete.Expression domain = visitExpr(ctx.expr(0));
    Concrete.Expression codomain = visitExpr(ctx.expr(1));
    if (domain == null || codomain == null) {
      return null;
    }

    List<Concrete.TypeArgument> arguments = new ArrayList<>(1);
    arguments.add(new Concrete.TypeArgument(domain.getPosition(), true, domain));
    return new Concrete.PiExpression(tokenPosition(ctx.getToken(ARROW, 0).getSymbol()), arguments, codomain);
  }

  @Override
  public Concrete.Expression visitTuple(TupleContext ctx) {
    if (ctx == null) return null;
    if (ctx.expr().size() == 1) {
      return visitExpr(ctx.expr(0));
    } else {
      List<Concrete.Expression> fields = new ArrayList<>(ctx.expr().size());
      for (ExprContext exprCtx : ctx.expr()) {
        Concrete.Expression expr = visitExpr(exprCtx);
        if (expr == null) return null;
        fields.add(expr);
      }
      return new Concrete.TupleExpression(tokenPosition(ctx.getStart()), fields);
    }
  }

  private List<Concrete.Argument> visitLamTele(TeleContext tele) {
    List<Concrete.Argument> arguments = new ArrayList<>(3);
    if (tele instanceof TeleLiteralContext) {
      LiteralContext literalContext = ((TeleLiteralContext) tele).literal();
      if (literalContext instanceof IdContext && ((IdContext) literalContext).name() instanceof NameIdContext) {
        String name = ((NameIdContext) ((IdContext) literalContext).name()).ID().getText();
        arguments.add(new Concrete.NameArgument(tokenPosition(((NameIdContext) ((IdContext) literalContext).name()).ID().getSymbol()), true, name));
      } else
      if (literalContext instanceof UnknownContext) {
        arguments.add(new Concrete.NameArgument(tokenPosition(literalContext.getStart()), true, null));
      } else {
        myErrorReporter.report(new ParserError(tokenPosition(literalContext.getStart()), "Unexpected token. Expected an identifier."));
        return null;
      }
    } else {
      boolean explicit = tele instanceof ExplicitContext;
      TypedExprContext typedExpr = explicit ? ((ExplicitContext) tele).typedExpr() : ((ImplicitContext) tele).typedExpr();
      ExprContext varsExpr;
      Concrete.Expression typeExpr;
      if (typedExpr instanceof TypedContext) {
        varsExpr = ((TypedContext) typedExpr).expr(0);
        typeExpr = visitExpr(((TypedContext) typedExpr).expr(1));
        if (typeExpr == null) return null;
      } else {
        varsExpr = ((NotTypedContext) typedExpr).expr();
        typeExpr = null;
      }
      List<Concrete.NameArgument> vars = getVars(varsExpr);
      if (vars == null) return null;

      if (typeExpr == null) {
        if (explicit) {
          arguments.addAll(vars);
        } else {
          for (Concrete.NameArgument var : vars) {
            arguments.add(new Concrete.NameArgument(var.getPosition(), false, var.getName()));
          }
        }
      } else {
        List<String> args = new ArrayList<>(vars.size());
        for (Concrete.NameArgument var : vars) {
          args.add(var.getName());
        }
        arguments.add(new Concrete.TelescopeArgument(tokenPosition(tele.getStart()), explicit, args, typeExpr));
      }
    }
    return arguments;
  }

  private List<Concrete.Argument> visitLamTeles(List<TeleContext> tele) {
    List<Concrete.Argument> arguments = new ArrayList<>();
    for (TeleContext arg : tele) {
      List<Concrete.Argument> arguments1 = visitLamTele(arg);
      if (arguments1 == null) return null;
      arguments.addAll(arguments1);
    }
    return arguments;
  }

  @Override
  public Concrete.Expression visitLam(LamContext ctx) {
    if (ctx == null) return null;
    List<Concrete.Argument> args = visitLamTeles(ctx.tele());
    Concrete.Expression body = visitExpr(ctx.expr());
    if (args == null || body == null) {
      return null;
    }
    return new Concrete.LamExpression(tokenPosition(ctx.getStart()), args, body);
  }

  @Override
  public Concrete.DefCallExpression visitId(IdContext ctx) {
    if (ctx == null) return null;
    String name = visitName(ctx.name());
    if (name == null) return null;
    return new Concrete.DefCallExpression(tokenPosition(ctx.name().getStart()), null, name);
  }

  @Override
  public Concrete.Expression visitPolyUniverse(PolyUniverseContext ctx) {
    if (ctx.expr().isEmpty()) {
      return new Concrete.TypeOmegaExpression(tokenPosition(ctx.getStart()));
    }
    Concrete.Expression plevel = visitExpr(ctx.expr(0));
    Concrete.Expression hlevel = visitExpr(ctx.expr(1));
    return new Concrete.PolyUniverseExpression(tokenPosition(ctx.getStart()), plevel, hlevel);
  }

  @Override
  public Concrete.UniverseExpression visitUniverse(UniverseContext ctx) {
    if (ctx == null) return null;
    Abstract.UniverseExpression.Universe universe = new Abstract.UniverseExpression.Universe(Integer.valueOf(ctx.UNIVERSE().getText().substring("\\Type".length())), Abstract.UniverseExpression.Universe.NOT_TRUNCATED);
    return new Concrete.UniverseExpression(tokenPosition(ctx.UNIVERSE().getSymbol()), universe);
  }

  @Override
  public Concrete.UniverseExpression visitTruncatedUniverse(TruncatedUniverseContext ctx) {
    if (ctx == null) return null;
    String text = ctx.TRUNCATED_UNIVERSE().getText();
    int indexOfMinusSign = text.indexOf('-');
    return new Concrete.UniverseExpression(tokenPosition(ctx.TRUNCATED_UNIVERSE().getSymbol()), new Abstract.UniverseExpression.Universe(Integer.valueOf(text.substring(indexOfMinusSign + "-Type".length())), Integer.valueOf(text.substring(1, indexOfMinusSign))));
  }

  @Override
  public Concrete.UniverseExpression visitProp(PropContext ctx) {
    if (ctx == null) return null;
    return new Concrete.UniverseExpression(tokenPosition(ctx.PROP().getSymbol()), new Abstract.UniverseExpression.Universe(0, Abstract.UniverseExpression.Universe.PROP));
  }

  @Override
  public Concrete.UniverseExpression visitSet(SetContext ctx) {
    if (ctx == null) return null;
    return new Concrete.UniverseExpression(tokenPosition(ctx.SET().getSymbol()), new Abstract.UniverseExpression.Universe(Integer.valueOf(ctx.SET().getText().substring("\\Set".length())), Abstract.UniverseExpression.Universe.SET));
  }

  private List<Concrete.TypeArgument> visitTeles(List<TeleContext> teles) {
    List<Concrete.TypeArgument> arguments = new ArrayList<>(teles.size());
    for (TeleContext tele : teles) {
      boolean explicit = !(tele instanceof ImplicitContext);
      TypedExprContext typedExpr;
      if (explicit) {
        if (tele instanceof ExplicitContext) {
          typedExpr = ((ExplicitContext) tele).typedExpr();
        } else
        if (tele instanceof TeleLiteralContext) {
          Concrete.Expression expr = visitExpr(((TeleLiteralContext) tele).literal());
          if (expr == null) {
            return null;
          }
          arguments.add(new Concrete.TypeArgument(true, expr));
          continue;
        } else {
          return null;
        }
      } else {
        typedExpr = ((ImplicitContext) tele).typedExpr();
      }
      if (typedExpr instanceof TypedContext) {
        Concrete.Expression type = visitExpr(((TypedContext) typedExpr).expr(1));
        List<Concrete.NameArgument> args = getVars(((TypedContext) typedExpr).expr(0));
        if (type == null || args == null) {
          return null;
        }

        List<String> vars = new ArrayList<>(args.size());
        for (Concrete.NameArgument arg : args) {
          vars.add(arg.getName());
        }
        arguments.add(new Concrete.TelescopeArgument(tokenPosition(tele.getStart()), explicit, vars, type));
      } else {
        Concrete.Expression expr = visitExpr(((NotTypedContext) typedExpr).expr());
        if (expr == null) {
          return null;
        }
        arguments.add(new Concrete.TypeArgument(explicit, expr));
      }
    }
    return arguments;
  }

  @Override
  public Concrete.Expression visitAtomLiteral(AtomLiteralContext ctx) {
    if (ctx == null) return null;
    return visitExpr(ctx.literal());
  }

  @Override
  public Concrete.NumericLiteral visitAtomNumber(AtomNumberContext ctx) {
    if (ctx == null) return null;
    return new Concrete.NumericLiteral(tokenPosition(ctx.NUMBER().getSymbol()), Integer.valueOf(ctx.NUMBER().getText()));
  }

  @Override
  public Concrete.SigmaExpression visitSigma(SigmaContext ctx) {
    if (ctx == null) return null;
    List<Concrete.TypeArgument> args = visitTeles(ctx.tele());
    if (args == null) {
      return null;
    }

    for (Concrete.TypeArgument arg : args) {
      if (!arg.getExplicit()) {
        myErrorReporter.report(new ParserError(arg.getPosition(), "Fields in sigma types must be explicit"));
      }
    }
    return new Concrete.SigmaExpression(tokenPosition(ctx.getStart()), args);
  }

  @Override
  public Concrete.PiExpression visitPi(PiContext ctx) {
    if (ctx == null) return null;
    List<Concrete.TypeArgument> args = visitTeles(ctx.tele());
    Concrete.Expression codomain = visitExpr(ctx.expr());
    if (args == null || codomain == null) {
      return null;
    }
    return new Concrete.PiExpression(tokenPosition(ctx.getStart()), args, codomain);
  }

  private Concrete.Expression visitAtoms(Concrete.Expression expr, List<ArgumentContext> arguments) {
    if (expr == null) {
      return null;
    }
    for (ArgumentContext argument : arguments) {
      if (argument instanceof ArgumentLevelContext) {
        expr = new Concrete.ApplyLevelExpression(expr.getPosition(), expr, visitExpr(((ArgumentLevelContext) argument).expr()));
        continue;
      }

      boolean explicit = argument instanceof ArgumentExplicitContext;
      Concrete.Expression expr1;
      if (explicit) {
        expr1 = visitAtomFieldsAcc(((ArgumentExplicitContext) argument).atomFieldsAcc());
      } else {
        expr1 = visitExpr(((ArgumentImplicitContext) argument).expr());
      }
      if (expr1 == null) return null;
      expr = new Concrete.AppExpression(expr.getPosition(), expr, new Concrete.ArgumentExpression(expr1, explicit, false));
    }
    return expr;
  }

  @Override
  public Concrete.Expression visitBinOp(BinOpContext ctx) {
    if (ctx == null) return null;
    Concrete.Expression left = null;
    Concrete.DefCallExpression binOp = null;
    List<Abstract.BinOpSequenceElem> sequence = new ArrayList<>(ctx.binOpLeft().size());

    for (BinOpLeftContext leftContext : ctx.binOpLeft()) {
      String name = (String) visit(leftContext.infix());
      Concrete.Expression expr = visitAtoms(visitAtomFieldsAcc(leftContext.atomFieldsAcc()), leftContext.argument());
      if (expr == null) {
        continue;
      }
      if (leftContext.maybeNew() instanceof WithNewContext) {
        expr = new Concrete.NewExpression(tokenPosition(leftContext.getStart()), expr);
      }

      if (left == null) {
        left = expr;
      } else {
        sequence.add(new Abstract.BinOpSequenceElem(binOp, expr));
      }
      binOp = new Concrete.DefCallExpression(tokenPosition(leftContext.infix().getStart()), null, name);
    }

    Concrete.Expression expr = visitAtoms(visitAtomFieldsAcc(ctx.atomFieldsAcc()), ctx.argument());
    if (expr == null) {
      return null;
    }
    if (ctx.maybeNew() instanceof WithNewContext) {
      expr = new Concrete.NewExpression(tokenPosition(ctx.getStart()), expr);
    }

    if (left == null) {
      return expr;
    }

    sequence.add(new Abstract.BinOpSequenceElem(binOp, expr));
    return new Concrete.BinOpSequenceExpression(tokenPosition(ctx.getStart()), left, sequence);
  }

  @Override
  public Concrete.Expression visitAtomFieldsAcc(AtomFieldsAccContext ctx) {
    if (ctx == null) return null;
    Concrete.Expression expression = visitExpr(ctx.atom());
    if (expression == null || ctx.fieldAcc() == null) {
      return null;
    }

    for (FieldAccContext fieldAccContext : ctx.fieldAcc()) {
      if (fieldAccContext instanceof ClassFieldContext) {
        String name = visitName(((ClassFieldContext) fieldAccContext).name());
        if (name == null) return null;
        expression = new Concrete.DefCallExpression(tokenPosition(fieldAccContext.getStart()), expression, name);
      } else
      if (fieldAccContext instanceof SigmaFieldContext) {
        expression = new Concrete.ProjExpression(tokenPosition(fieldAccContext.getStart()), expression, Integer.valueOf(((SigmaFieldContext) fieldAccContext).NUMBER().getText()) - 1);
      } else {
        throw new IllegalStateException();
      }
    }

    if (ctx.implementStatements() != null) {
      List<Concrete.ClassFieldImpl> implementStatements = new ArrayList<>(ctx.implementStatements().implementStatement().size());
      for (ImplementStatementContext implementStatement : ctx.implementStatements().implementStatement()) {
        String name = visitName(implementStatement.name());
        Concrete.Expression expression1 = visitExpr(implementStatement.expr());
        if (name != null && expression1 != null) {
          implementStatements.add(new Concrete.ClassFieldImpl(tokenPosition(implementStatement.name().getStart()), name, expression1));
        }
      }
      expression = new Concrete.ClassExtExpression(tokenPosition(ctx.getStart()), expression, implementStatements);
    }
    return expression;
  }

  @Override
  public String visitInfixBinOp(InfixBinOpContext ctx) {
    if (ctx == null) return null;
    return ctx.BIN_OP().getText();
  }

  @Override
  public String visitInfixId(InfixIdContext ctx) {
    if (ctx == null) return null;
    return ctx.ID().getText();
  }

  private String visitName(NameContext ctx) {
    if (ctx == null) return null;
    return (String) visit(ctx);
  }

  @Override
  public String visitNameId(NameIdContext ctx) {
    if (ctx == null) return null;
    return ctx.ID().getText();
  }

  @Override
  public String visitNameBinOp(NameBinOpContext ctx) {
    if (ctx == null) return null;
    return ctx.BIN_OP().getText();
  }

  @Override
  public Concrete.Expression visitExprElim(ExprElimContext ctx) {
    if (ctx == null) return null;
    List<Concrete.Clause> clauses = new ArrayList<>(ctx.clause().size());

    List<Concrete.Expression> elimExprs = new ArrayList<>();
    for (ExprContext exprCtx : ctx.expr()) {
      Concrete.Expression expr = visitExpr(exprCtx);
      if (expr == null) {
        return null;
      }
      elimExprs.add(expr);
    }

    if (ctx.elimCase() instanceof ElimContext) {
      for (Concrete.Expression elimExpr : elimExprs) {
        if (!(elimExpr instanceof Concrete.DefCallExpression && ((Concrete.DefCallExpression) elimExpr).getExpression() == null)) {
          myErrorReporter.report(new ParserError(elimExpr.getPosition(), "\\elim can be applied only to a local variable"));
          return null;
        }
      }
    }

    for (ClauseContext clauseCtx : ctx.clause()) {
      List<Concrete.Pattern> patterns = new ArrayList<>();
      for (PatternContext patternCtx : clauseCtx.pattern()) {
        patterns.add((Concrete.Pattern) visit(patternCtx));
      }

      if (patterns.size() != ctx.expr().size()) {
        myErrorReporter.report(new ParserError(tokenPosition(clauseCtx.getStart()), "Expected: " + ctx.expr().size() + " patterns, got: " + patterns.size()));
        return null;
      }

      if (clauseCtx.arrow() == null) {
        clauses.add(new Concrete.Clause(tokenPosition(clauseCtx.start), patterns, null, null));
      } else {
        Abstract.Definition.Arrow arrow = clauseCtx.arrow() instanceof ArrowRightContext ? Abstract.Definition.Arrow.RIGHT : Abstract.Definition.Arrow.LEFT;

        Concrete.Expression expr = visitExpr(clauseCtx.expr());
        if (expr == null) {
          return null;
        }

        clauses.add(new Concrete.Clause(tokenPosition(clauseCtx.getStart()), patterns, arrow, expr));
      }
    }

    return ctx.elimCase() instanceof ElimContext ?
        new Concrete.ElimExpression(tokenPosition(ctx.getStart()), elimExprs, clauses) :
        new Concrete.CaseExpression(tokenPosition(ctx.getStart()), elimExprs, clauses);
  }

  @Override
  public Concrete.LetClause visitLetClause(LetClauseContext ctx) {
    String name = ctx.ID().getText();

    List<Concrete.Argument> arguments = visitFunctionArguments(ctx.tele());
    Concrete.Expression resultType = ctx.typeAnnotation() == null ? null : visitExpr(ctx.typeAnnotation().expr());
    Abstract.Definition.Arrow arrow = visitArrow(ctx.arrow());
    Concrete.Expression term = visitExpr(ctx.expr());

    if (arrow == null || term == null) {
      return null;
    }

    return new Concrete.LetClause(tokenPosition(ctx.getStart()), name, new ArrayList<>(arguments), resultType, arrow, term);
  }

  @Override
  public Concrete.LetExpression visitLet(LetContext ctx) {
    List<Concrete.LetClause> clauses = new ArrayList<>();
    for (LetClauseContext clauseCtx : ctx.letClause()) {
      Concrete.LetClause clause = visitLetClause(clauseCtx);
      if (clause == null) {
        continue;
      }
      clauses.add(visitLetClause(clauseCtx));
    }

    Concrete.Expression expr = visitExpr(ctx.expr());
    if (expr == null) {
      return null;
    }
    return new Concrete.LetExpression(tokenPosition(ctx.getStart()), clauses, expr);
  }

  private Concrete.Position tokenPosition(Token token) {
    return new Concrete.Position(myModule, token.getLine(), token.getCharPositionInLine());
  }
}
