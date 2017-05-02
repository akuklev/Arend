package com.jetbrains.jetpad.vclang.frontend.resolving.visitor;

import com.jetbrains.jetpad.vclang.core.context.Utils;
import com.jetbrains.jetpad.vclang.error.Error;
import com.jetbrains.jetpad.vclang.error.GeneralError;
import com.jetbrains.jetpad.vclang.frontend.resolving.NamespaceProviders;
import com.jetbrains.jetpad.vclang.frontend.resolving.ResolveListener;
import com.jetbrains.jetpad.vclang.naming.NameResolver;
import com.jetbrains.jetpad.vclang.naming.error.DuplicateDefinitionError;
import com.jetbrains.jetpad.vclang.naming.error.NotInScopeError;
import com.jetbrains.jetpad.vclang.naming.error.WrongDefinition;
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace;
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace;
import com.jetbrains.jetpad.vclang.naming.scope.*;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor;
import com.jetbrains.jetpad.vclang.term.AbstractStatementVisitor;
import com.jetbrains.jetpad.vclang.util.Pair;

import java.util.*;

public class DefinitionResolveNameVisitor implements AbstractDefinitionVisitor<Scope, Void>, AbstractStatementVisitor<Scope, Scope> {
  private final NamespaceProviders myNsProviders;
  private List<Pair<String, Abstract.ReferableSourceNode>> myContext;
  private final NameResolver myNameResolver;
  private final ResolveListener myResolveListener;

  public DefinitionResolveNameVisitor(NamespaceProviders nsProviders, NameResolver nameResolver, ResolveListener resolveListener) {
    this(nsProviders, new ArrayList<>(), nameResolver, resolveListener);
  }

  private DefinitionResolveNameVisitor(NamespaceProviders nsProviders, List<Pair<String, Abstract.ReferableSourceNode>> context,
                                      NameResolver nameResolver, ResolveListener resolveListener) {
    myNsProviders = nsProviders;
    myContext = context;
    myNameResolver = nameResolver;
    myResolveListener = resolveListener;
  }

  @Override
  public Void visitFunction(Abstract.FunctionDefinition def, Scope parentScope) {
    Scope scope = new FunctionScope(parentScope, new NamespaceScope(myNsProviders.statics.forDefinition(def)));

    for (Abstract.Statement statement : def.getGlobalStatements()) {
      if (statement instanceof Abstract.NamespaceCommandStatement) {
        scope = statement.accept(this, scope);
      }
    }
    for (Abstract.Statement statement : def.getGlobalStatements()) {
      if (!(statement instanceof Abstract.NamespaceCommandStatement)) {
        scope = statement.accept(this, scope);
      }
    }

    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(myNsProviders, scope, myContext, myNameResolver, myResolveListener);
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      exprVisitor.visitArguments(def.getArguments());

      Abstract.Expression resultType = def.getResultType();
      if (resultType != null) {
        resultType.accept(exprVisitor, null);
      }

      Abstract.Expression term = def.getTerm();
      if (term != null) {
        term.accept(exprVisitor, null);
      }
    }

    return null;
  }

  @Override
  public Void visitClassField(Abstract.ClassField def, Scope parentScope) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      Abstract.Expression resultType = def.getResultType();
      if (resultType != null) {
        resultType.accept(new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener), null);
      }
    }
    return null;
  }

  @Override
  public Void visitData(Abstract.DataDefinition def, Scope parentScope) {
    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener);
    try (Utils.CompleteContextSaver<Pair<String, Abstract.ReferableSourceNode>> saver = new Utils.CompleteContextSaver<>(myContext)) {
      for (Abstract.TypeArgument parameter : def.getParameters()) {
        parameter.getType().accept(exprVisitor, null);
        if (parameter instanceof Abstract.TelescopeArgument) {
          for (Abstract.ReferableSourceNode referable : ((Abstract.TelescopeArgument) parameter).getReferableList()) {
            if (referable != null && referable.getName() != null && !referable.getName().equals("_")) {
              myContext.add(new Pair<>(referable.getName(), referable));
            }
          }
        }
      }

      for (Abstract.Constructor constructor : def.getConstructors()) {
        if (constructor.getPatterns() == null) {
          visitConstructor(constructor, parentScope);
        } else {
          myContext = saver.getOldContext();
          visitConstructor(constructor, parentScope);
          myContext = saver.getCurrentContext();
        }
      }

      if (def.getConditions() != null) {
        Scope scope = new DataScope(parentScope, new NamespaceScope(myNsProviders.statics.forDefinition(def)));
        exprVisitor = new ExpressionResolveNameVisitor(myNsProviders, scope, myContext, myNameResolver, myResolveListener);
        for (Abstract.Condition cond : def.getConditions()) {
          try (Utils.ContextSaver ignore = new Utils.ContextSaver(myContext)) {
            for (Abstract.PatternArgument patternArgument : cond.getPatterns()) {
              if (exprVisitor.visitPattern(patternArgument.getPattern())) {
                myResolveListener.replaceWithConstructor(patternArgument);
              }
              exprVisitor.resolvePattern(patternArgument.getPattern());
            }
            cond.getTerm().accept(exprVisitor, null);
          }
        }
      }
    }

    return null;
  }

  @Override
  public Void visitConstructor(Abstract.Constructor def, Scope parentScope) {
    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener);
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      if (def.getPatterns() != null) {
        for (Abstract.PatternArgument patternArg : def.getPatterns()) {
          if (exprVisitor.visitPattern(patternArg.getPattern())) {
            myResolveListener.replaceWithConstructor(patternArg);
          }
          exprVisitor.resolvePattern(patternArg.getPattern());
        }
      }

      exprVisitor.visitArguments(def.getArguments());
    }

    return null;
  }

  private void mergeNames(Scope parent, Scope child) {
    Set<String> parentNames = parent.getNames();
    if (!parentNames.isEmpty()) {
      Set<String> childNames = child.getNames();
      if (!childNames.isEmpty()) {
        for (String name : parentNames) {
          if (childNames.contains(name)) {
            myResolveListener.report(new DuplicateDefinitionError(Error.Level.WARNING, parent.resolveName(name), child.resolveName(name)));
          }
        }
      }
    }
  }

  @Override
  public Void visitClass(Abstract.ClassDefinition def, Scope parentScope) {
    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener);
    for (Abstract.SuperClass superClass : def.getSuperClasses()) {
      superClass.getSuperClass().accept(exprVisitor, null);
    }

    try {
      Scope staticNamespace = new NamespaceScope(myNsProviders.statics.forDefinition(def));
      Scope staticScope = new StaticClassScope(parentScope, staticNamespace);
      for (Abstract.Statement statement : def.getGlobalStatements()) {
        if (statement instanceof Abstract.NamespaceCommandStatement) {
          staticScope = statement.accept(this, staticScope);
        }
      }
      for (Abstract.Statement statement : def.getGlobalStatements()) {
        if (!(statement instanceof Abstract.NamespaceCommandStatement)) {
          staticScope = statement.accept(this, staticScope);
        }
      }

      try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
        for (Abstract.TypeArgument polyParam : def.getPolyParameters()) {
          polyParam.getType().accept(exprVisitor, null);
          if (polyParam instanceof Abstract.TelescopeArgument) {
            for (Abstract.ReferableSourceNode referable : ((Abstract.TelescopeArgument) polyParam).getReferableList()) {
              if (referable != null && referable.getName() != null && referable.getName().equals("_")) {
                myContext.add(new Pair<>(referable.getName(), referable));
              }
            }
          }
        }

        Scope child = new NamespaceScope(myNsProviders.dynamics.forClass(def));
        mergeNames(staticNamespace, child);
        Scope dynamicScope = new DynamicClassScope(staticScope, child);

        for (Abstract.ClassField field : def.getFields()) {
          field.accept(this, dynamicScope);
        }
        for (Abstract.Implementation implementation : def.getImplementations()) {
          implementation.accept(this, dynamicScope);
        }
        for (Abstract.Definition definition : def.getInstanceDefinitions()) {
          definition.accept(this, dynamicScope);
        }
      }
    } catch (Namespace.InvalidNamespaceException e) {
      myResolveListener.report(e.toError());
    }

    return null;
  }

  @Override
  public Void visitImplement(Abstract.Implementation def, Scope parentScope) {
    Abstract.ClassField referable = myNameResolver.resolveClassField(def.getParentDefinition(), def.getName(), myNsProviders.dynamics, myResolveListener, def);
    if (referable != null) {
      myResolveListener.implementResolved(def, referable);
    }

    def.getImplementation().accept(new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener), null);
    return null;
  }

  @Override
  public Void visitClassView(Abstract.ClassView def, Scope parentScope) {
    def.getUnderlyingClassDefCall().accept(new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener), null);
    Abstract.ReferableSourceNode resolvedUnderlyingClass = def.getUnderlyingClassDefCall().getReferent();
    if (!(resolvedUnderlyingClass instanceof Abstract.ClassDefinition)) {
      if (resolvedUnderlyingClass != null) {
        myResolveListener.report(new WrongDefinition("Expected a class", resolvedUnderlyingClass, def));
      }
      return null;
    }

    Namespace dynamicNamespace = myNsProviders.dynamics.forClass((Abstract.ClassDefinition) resolvedUnderlyingClass);
    Abstract.Definition resolvedClassifyingField = dynamicNamespace.resolveName(def.getClassifyingFieldName());
    if (!(resolvedClassifyingField instanceof Abstract.ClassField)) {
      myResolveListener.report(resolvedClassifyingField != null ? new WrongDefinition("Expected a class field", resolvedClassifyingField, def) : new NotInScopeError(def, def.getClassifyingFieldName()));
      return null;
    }

    myResolveListener.classViewResolved(def, (Abstract.ClassField) resolvedClassifyingField);

    for (Abstract.ClassViewField viewField : def.getFields()) {
      Abstract.ClassField classField = myNameResolver.resolveClassField((Abstract.ClassDefinition) resolvedUnderlyingClass, viewField.getUnderlyingFieldName(), myNsProviders.dynamics, myResolveListener, viewField);
      if (classField != null) {
        myResolveListener.classViewFieldResolved(viewField, classField);
      }
    }
    return null;
  }

  @Override
  public Void visitClassViewField(Abstract.ClassViewField def, Scope parentScope) {
    throw new IllegalStateException();
  }

  @Override
  public Void visitClassViewInstance(Abstract.ClassViewInstance def, Scope parentScope) {
    ExpressionResolveNameVisitor exprVisitor = new ExpressionResolveNameVisitor(myNsProviders, parentScope, myContext, myNameResolver, myResolveListener);
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      exprVisitor.visitArguments(def.getArguments());
      exprVisitor.visitReference(def.getClassView(), null);
      if (def.getClassView().getReferent() instanceof Abstract.ClassView) {
        exprVisitor.visitClassFieldImpls(def.getClassFieldImpls(), (Abstract.ClassView) def.getClassView().getReferent(), null);
        boolean ok = false;
        for (Abstract.ClassFieldImpl impl : def.getClassFieldImpls()) {
          if (impl.getImplementedField() == ((Abstract.ClassView) def.getClassView().getReferent()).getClassifyingField()) {
            ok = true;
            Abstract.Expression expr = impl.getImplementation();
            while (expr instanceof Abstract.AppExpression) {
              expr = ((Abstract.AppExpression) expr).getFunction();
            }
            if (expr instanceof Abstract.ReferenceExpression && ((Abstract.ReferenceExpression) expr).getReferent() instanceof Abstract.Definition) {
              myResolveListener.classViewInstanceResolved(def, (Abstract.Definition) ((Abstract.ReferenceExpression) expr).getReferent());
            } else {
              myResolveListener.report(new GeneralError("Expected a definition applied to arguments", impl.getImplementation()));
            }
          }
        }
        if (!ok) {
          myResolveListener.report(new GeneralError("Classifying field is not implemented", def));
        }
      } else {
        myResolveListener.report(new WrongDefinition("Expected a class view", def.getClassView().getReferent(), def));
      }
    }

    return null;
  }

  @Override
  public Scope visitDefine(Abstract.DefineStatement stat, Scope parentScope) {
    stat.getDefinition().accept(this, parentScope);
    return parentScope;
  }

  @Override
  public Scope visitNamespaceCommand(Abstract.NamespaceCommandStatement stat, Scope parentScope) {
    if (stat.getResolvedClass() == null) {
      final Abstract.Definition referredClass;
      if (stat.getModulePath() == null) {
        if (stat.getPath().isEmpty()) {
          myResolveListener.report(new GeneralError("Structure error: empty namespace command", stat));
          return parentScope;
        }
        referredClass = myNameResolver.resolveDefinition(parentScope, stat.getPath(), myNsProviders.statics);
      } else {
        ModuleNamespace moduleNamespace = myNameResolver.resolveModuleNamespace(stat.getModulePath(), myNsProviders.modules);
        Abstract.ClassDefinition moduleClass = moduleNamespace != null ? moduleNamespace.getRegisteredClass() : null;
        if (moduleClass == null) {
          myResolveListener.report(new GeneralError("Module not found: " + stat.getModulePath(), stat));
          return parentScope;
        }
        if (stat.getPath().isEmpty()) {
          referredClass = moduleNamespace.getRegisteredClass();
        } else {
          referredClass = myNameResolver.resolveDefinition(new NamespaceScope(myNsProviders.statics.forDefinition(moduleClass)), stat.getPath(), myNsProviders.statics);
        }
      }

      if (referredClass == null) {
        myResolveListener.report(new GeneralError("Class not found", stat));
        return parentScope;
      }
      myResolveListener.nsCmdResolved(stat, referredClass);
    }

    if (stat.getKind().equals(Abstract.NamespaceCommandStatement.Kind.OPEN)) {
      Scope scope = new NamespaceScope(myNsProviders.statics.forDefinition(stat.getResolvedClass()));
      if (stat.getNames() != null) {
        scope = new FilteredScope(scope, new HashSet<>(stat.getNames()), !stat.isHiding());
      }
      mergeNames(scope, parentScope);
      parentScope = new OverridingScope(scope, parentScope);
    }

    return parentScope;
  }
}
