package org.arend.naming.scope;

import org.arend.naming.reference.ClassReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TypedReferable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class ClassFieldImplScope implements Scope {
  private final ClassReferable myReferable;
  private final boolean myWithSuperClasses;

  public ClassReferable getClassReference() {
    return myReferable;
  }

  public boolean withSuperClasses() {
    return myWithSuperClasses;
  }

  public ClassFieldImplScope(ClassReferable referable, boolean withSuperClasses) {
    myReferable = referable;
    myWithSuperClasses = withSuperClasses;
  }

  @Nullable
  @Override
  public Referable find(Predicate<Referable> pred) {
    Set<ClassReferable> visitedClasses = new HashSet<>();
    Deque<ClassReferable> toVisit = new ArrayDeque<>();
    toVisit.add(myReferable);

    while (!toVisit.isEmpty()) {
      ClassReferable classRef = toVisit.removeLast();
      if (!visitedClasses.add(classRef)) {
        continue;
      }

      for (LocatedReferable referable : classRef.getFieldReferables()) {
        if (pred.test(referable)) {
          return referable;
        }
      }

      List<? extends ClassReferable> superClasses = classRef.getSuperClassReferences();
      if (myWithSuperClasses) {
        for (ClassReferable superClass : superClasses) {
          if (pred.test(superClass)) {
            return superClass;
          }
        }
      }

      for (int i = superClasses.size() - 1; i >= 0; i--) {
        toVisit.add(superClasses.get(i));
      }
    }

    return null;
  }

  @Nullable
  @Override
  public Scope resolveNamespace(String name, boolean onlyInternal) {
    Referable referable = resolveName(name);
    if (myWithSuperClasses && referable instanceof ClassReferable) {
      return new ClassFieldImplScope((ClassReferable) referable, true);
    }
    if (referable instanceof TypedReferable) {
      ClassReferable classRef = ((TypedReferable) referable).getTypeClassReference();
      if (classRef != null) {
        return new ClassFieldImplScope(classRef, false);
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public Scope getGlobalSubscope() {
    return EmptyScope.INSTANCE;
  }

  @Nonnull
  @Override
  public Scope getGlobalSubscopeWithoutOpens() {
    return EmptyScope.INSTANCE;
  }
}
